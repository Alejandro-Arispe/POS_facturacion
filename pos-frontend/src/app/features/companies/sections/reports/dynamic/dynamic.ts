import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../core/services/company-permissions.service';
import { DynamicReportResponse, DynamicReportService, ReportExportFormat } from '../../../../../core/services/dynamic-report.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-dynamic-reports',
  imports: [Navbar, Sidebar, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dynamic.html',
  styleUrl: './dynamic.css',
})
export class DynamicReports {
  private readonly route = inject(ActivatedRoute);
  private readonly reportService = inject(DynamicReportService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly promptControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(3)],
  });

  protected readonly report = signal<DynamicReportResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly exportingFormat = signal<ReportExportFormat | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly listening = signal(false);
  protected readonly voiceSupported =
    typeof window !== 'undefined' && !!((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition);
  protected readonly speechSupported = typeof window !== 'undefined' && 'speechSynthesis' in window;
  private recognition: any = null;
  private speakNextReport = false;

  protected readonly hasReport = computed(() => this.report() !== null);
  protected readonly reportId = computed(() => this.report()?.id_reporte ?? '');
  protected readonly reportTitle = computed(() => this.report()?.titulo ?? 'Reporte dinamico');
  protected readonly reportStatus = computed(() => this.report()?.estado ?? '');
  protected readonly reportDate = computed(() => this.report()?.fecha_generacion ?? '');
  protected readonly reportIdentifier = computed(() => this.report()?.identificador_plantilla ?? '');
  protected readonly reportSpecification = computed(() => this.report()?.especificacion ?? null);
  protected readonly specificationMetricas = computed(() => this.reportSpecification()?.metricas ?? []);
  protected readonly specificationDimensiones = computed(() => this.reportSpecification()?.dimensiones ?? []);
  protected readonly specificationFilters = computed(() => {
    const filters = this.reportSpecification()?.filtros ?? {};

    return Object.entries(filters).map(([key, value]) => ({
      key,
      label: this.toLabel(key),
      value,
    }));
  });
  protected readonly reportColumns = computed(() => {
    const columns = this.report()?.columnas ?? [];

    if (columns.length > 0) {
      return columns;
    }

    const firstRow = this.report()?.filas?.[0];

    return firstRow
      ? Object.keys(firstRow).map((name) => ({ nombre: name, etiqueta: this.toLabel(name), tipo: undefined }))
      : [];
  });
  protected readonly reportRows = computed(() => this.report()?.filas ?? []);
  protected readonly reportWarnings = computed(() => this.report()?.advertencias ?? []);
  protected readonly reportAggregates = computed(() => {
    const aggregates = this.report()?.agregados ?? {};

    return Object.entries(aggregates).map(([name, value]) => ({
      name,
      label: this.toLabel(name),
      value,
    }));
  });
  protected readonly clarificationQuestion = computed(() => {
    const specification = this.report()?.especificacion;

    if (!specification?.solicita_aclaracion) {
      return null;
    }

    return specification.pregunta?.trim() || 'El backend necesita una aclaración adicional para generar el reporte.';
  });
  protected readonly hasRows = computed(() => this.reportRows().length > 0);
  protected readonly hasAggregates = computed(() => this.reportAggregates().length > 0);
  protected readonly hasWarnings = computed(() => this.reportWarnings().length > 0);
  protected readonly showEmptyState = computed(() => {
    const report = this.report();

    return Boolean(report) && !this.clarificationQuestion() && !this.hasRows() && !this.hasAggregates();
  });

  /** Reporte por voz: dicta el pedido, se genera el reporte y se lee el resumen en voz alta. */
  protected toggleVoice(): void {
    if (this.listening()) {
      this.recognition?.stop();
      return;
    }
    const Recognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Recognition) {
      return;
    }
    const recognition = new Recognition();
    recognition.lang = 'es-ES';
    recognition.interimResults = false;
    recognition.onresult = (event: any) => {
      const transcript: string = event.results?.[0]?.[0]?.transcript ?? '';
      if (transcript.trim()) {
        this.promptControl.setValue(transcript.trim());
        this.speakNextReport = true;
        this.runReport();
      }
    };
    recognition.onerror = (event: any) => {
      this.listening.set(false);
      if (event?.error === 'not-allowed') {
        this.errorMessage.set('Debes permitir el acceso al micrófono para dictar el reporte.');
      }
    };
    recognition.onend = () => this.listening.set(false);
    this.recognition = recognition;
    this.errorMessage.set(null);
    this.listening.set(true);
    recognition.start();
  }

  protected speakSummary(): void {
    if (!this.speechSupported) {
      return;
    }
    const parts: string[] = [this.reportTitle()];
    const question = this.clarificationQuestion();
    if (question) {
      parts.push(question);
    } else {
      for (const aggregate of this.reportAggregates().slice(0, 4)) {
        parts.push(`${aggregate.label}: ${this.spokenValue(aggregate.value)}`);
      }
      const rows = this.reportRows();
      parts.push(rows.length === 1 ? 'Se encontró un registro.' : `Se encontraron ${rows.length} registros.`);
      const columns = this.reportColumns();
      for (const row of rows.slice(0, 3)) {
        const values = columns
          .slice(0, 3)
          .map((column) => this.spokenValue((row as Record<string, unknown>)[column.nombre]))
          .filter(Boolean);
        if (values.length) {
          parts.push(values.join(', '));
        }
      }
    }
    const utterance = new SpeechSynthesisUtterance(parts.join('. '));
    utterance.lang = 'es-ES';
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  }

  private spokenValue(value: unknown): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (typeof value === 'number') {
      return Number.isInteger(value) ? String(value) : value.toFixed(2);
    }
    return String(value);
  }

  protected runReport(): void {
    if (!this.companyId) {
      this.errorMessage.set('No se encontro el identificador de la empresa en la ruta.');
      return;
    }

    if (this.promptControl.invalid) {
      this.promptControl.markAsTouched();
      return;
    }

    const prompt = this.promptControl.value.trim();

    if (!prompt) {
      this.promptControl.setErrors({ required: true });
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    this.reportService.run(this.companyId, prompt).subscribe({
      next: (response) => {
        this.report.set(response);
        this.loading.set(false);
        if (this.speakNextReport) {
          this.speakNextReport = false;
          this.speakSummary();
        }
      },
      error: (error: unknown) => {
        this.report.set(null);
        this.loading.set(false);
        this.errorMessage.set(this.getErrorMessage(error));
      },
    });
  }
  protected exportReport(format: ReportExportFormat): void {
    const report = this.report();

    if (!this.hasPermission('REPORTE_EXPORTAR')) {
      this.errorMessage.set('No tienes permiso para exportar reportes.');
      return;
    }

    if (!report || this.exportingFormat()) {
      return;
    }

    if (format === 'excel' && (!this.hasRows() || this.reportColumns().length === 0)) {
      this.errorMessage.set('No hay filas y columnas para exportar a Excel.');
      return;
    }

    this.exportingFormat.set(format);
    this.errorMessage.set(null);

    try {
      if (format === 'excel') {
        this.exportExcelReport();
      } else {
        this.exportPdfReport(report);
      }
    } catch (error: unknown) {
      this.errorMessage.set(this.getErrorMessage(error));
    } finally {
      this.exportingFormat.set(null);
    }
  }

  protected formatValue(value: unknown, type?: string): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }

    if (Array.isArray(value)) {
      return value.map((item) => this.formatValue(item)).join(', ');
    }

    if (type === 'entero' || type === 'decimal' || type === 'moneda') {
      const numericValue = Number(value);

      if (!Number.isFinite(numericValue)) {
        return String(value);
      }

      return new Intl.NumberFormat('es-CO', {
        minimumFractionDigits: type === 'entero' ? 0 : 2,
        maximumFractionDigits: type === 'entero' ? 0 : 2,
      }).format(numericValue);
    }

    if (type === 'booleano') {
      return value ? 'Sí' : 'No';
    }

    return String(value);
  }

  protected formatLabel(value: string): string {
    return this.toLabel(value);
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    return this.companyPermissionsService.permissions()[permission] === true;
  }

  private exportExcelReport(): void {
    const columns = this.reportColumns();
    const rows = this.reportRows();
    const headerCells = columns
      .map((column) => `<th>${this.escapeHtml(column.etiqueta ?? this.toLabel(column.nombre))}</th>`)
      .join('');
    const bodyRows = rows
      .map((row) => {
        const cells = columns
          .map((column) => `<td>${this.escapeHtml(this.formatValue(row[column.nombre], column.tipo))}</td>`)
          .join('');

        return `<tr>${cells}</tr>`;
      })
      .join('');
    const worksheet = `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    table { border-collapse: collapse; font-family: Arial, sans-serif; font-size: 12px; }
    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: left; }
    th { background: #e2e8f0; font-weight: 700; }
  </style>
</head>
<body>
  <table>
    <thead><tr>${headerCells}</tr></thead>
    <tbody>${bodyRows}</tbody>
  </table>
</body>
</html>`;

    this.downloadBlob(
      new Blob([worksheet], { type: 'application/vnd.ms-excel;charset=utf-8' }),
      `${this.slugify(this.reportTitle())}.xls`,
    );
  }

  private exportPdfReport(report: DynamicReportResponse): void {
    const lines: string[] = [
      this.reportTitle(),
      `Empresa: ${this.companyId}`,
      `ID reporte: ${report.id_reporte ?? 'Sin ID'}`,
      `Estado: ${report.estado ? this.toLabel(report.estado) : 'Sin estado'}`,
      `Fecha: ${report.fecha_generacion ?? 'Sin fecha'}`,
      '',
    ];

    if (this.hasAggregates()) {
      lines.push('Resumen');
      this.reportAggregates().forEach((aggregate) => {
        lines.push(`${aggregate.label}: ${this.formatValue(aggregate.value)}`);
      });
      lines.push('');
    }

    const specification = this.reportSpecification();

    if (specification) {
      lines.push('Especificacion');
      lines.push(`Confianza: ${specification.confianza ?? 'Sin dato'}`);
      lines.push(`Formato: ${specification.formato ? this.toLabel(specification.formato) : 'Sin dato'}`);
      lines.push(`Solicita aclaracion: ${specification.solicita_aclaracion ? 'Si' : 'No'}`);

      if (specification.pregunta) {
        lines.push(`Pregunta: ${specification.pregunta}`);
      }

      lines.push('');
    }

    if (this.hasWarnings()) {
      lines.push('Advertencias');
      this.reportWarnings().forEach((warning) => lines.push(`- ${warning}`));
      lines.push('');
    }

    if (this.hasRows()) {
      const columns = this.reportColumns();
      lines.push('Tabla dinamica');
      lines.push(columns.map((column) => column.etiqueta ?? this.toLabel(column.nombre)).join(' | '));
      this.reportRows().forEach((row) => {
        lines.push(columns.map((column) => this.formatValue(row[column.nombre], column.tipo)).join(' | '));
      });
    }

    const pdf = this.createPdfDocument(lines);

    this.downloadBlob(new Blob([pdf.buffer as ArrayBuffer], { type: 'application/pdf' }), `${this.slugify(this.reportTitle())}.pdf`);
  }

  private createPdfDocument(lines: string[]): Uint8Array {
    const pageWidth = 595;
    const pageHeight = 842;
    const marginX = 42;
    const startY = 800;
    const lineHeight = 16;
    const maxChars = 92;
    const pages: string[][] = [[]];

    lines.flatMap((line) => this.wrapPdfLine(line, maxChars)).forEach((line) => {
      const currentPage = pages[pages.length - 1];

      if (currentPage.length >= 46) {
        pages.push([]);
      }

      pages[pages.length - 1].push(line);
    });

    const objects: string[] = [
      '<< /Type /Catalog /Pages 2 0 R >>',
      '',
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    ];
    const pageObjectNumbers: number[] = [];
    const fontObject = 3;
    const addObject = (value: string): number => {
      objects.push(value);
      return objects.length;
    };

    pages.forEach((pageLines) => {
      const streamLines = ['BT', '/F1 10 Tf'];

      pageLines.forEach((line, index) => {
        if (index === 0) {
          streamLines.push(`${marginX} ${startY} Td (${this.escapePdfText(line)}) Tj`);
        } else {
          streamLines.push(`0 -${lineHeight} Td (${this.escapePdfText(line)}) Tj`);
        }
      });

      streamLines.push('ET');
      const stream = streamLines.join('\n');
      const contentObject = addObject(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
      const pageObject = addObject(
        `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /Font << /F1 ${fontObject} 0 R >> >> /Contents ${contentObject} 0 R >>`,
      );

      pageObjectNumbers.push(pageObject);
    });

    const kids = pageObjectNumbers.map((objectNumber) => `${objectNumber} 0 R`).join(' ');
    objects[1] = `<< /Type /Pages /Kids [${kids}] /Count ${pageObjectNumbers.length} >>`;

    const chunks = ['%PDF-1.4\n'];
    const offsets = [0];

    objects.forEach((object, index) => {
      offsets.push(chunks.join('').length);
      chunks.push(`${index + 1} 0 obj\n${object}\nendobj\n`);
    });

    const xrefOffset = chunks.join('').length;
    chunks.push(`xref\n0 ${objects.length + 1}\n`);
    chunks.push('0000000000 65535 f \n');
    offsets.slice(1).forEach((offset) => chunks.push(`${offset.toString().padStart(10, '0')} 00000 n \n`));
    chunks.push(`trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`);

    return new TextEncoder().encode(chunks.join(''));
  }

  private wrapPdfLine(value: string, maxChars: number): string[] {
    const cleanValue = this.toPdfSafeText(value);

    if (cleanValue.length <= maxChars) {
      return [cleanValue];
    }

    const lines: string[] = [];
    let remaining = cleanValue;

    while (remaining.length > maxChars) {
      const breakpoint = remaining.lastIndexOf(' ', maxChars);
      const index = breakpoint > 20 ? breakpoint : maxChars;
      lines.push(remaining.slice(0, index).trim());
      remaining = remaining.slice(index).trim();
    }

    if (remaining) {
      lines.push(remaining);
    }

    return lines;
  }

  private toPdfSafeText(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^\x20-\x7E]/g, '')
      .trim();
  }

  private escapePdfText(value: string): string {
    return this.toPdfSafeText(value).replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
  private getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const backendMessage = this.extractBackendMessage(error.error);

      return backendMessage || error.message || 'No fue posible completar la solicitud.';
    }

    if (error instanceof Error) {
      return error.message;
    }

    return 'No fue posible completar la solicitud.';
  }

  private extractBackendMessage(payload: unknown): string | null {
    if (!payload) {
      return null;
    }

    if (typeof payload === 'string') {
      return payload;
    }

    if (typeof payload === 'object') {
      const typedPayload = payload as { message?: unknown; detail?: unknown; error?: unknown };
      const message = typedPayload.message ?? typedPayload.detail ?? typedPayload.error;

      return typeof message === 'string' ? message : null;
    }

    return null;
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');

    anchor.href = objectUrl;
    anchor.download = fileName;
    anchor.click();

    URL.revokeObjectURL(objectUrl);
  }

  private slugify(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-zA-Z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .toLowerCase() || 'reporte-dinamico';
  }

  private toLabel(value: string): string {
    return value
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/^./, (letter) => letter.toUpperCase());
  }
}

