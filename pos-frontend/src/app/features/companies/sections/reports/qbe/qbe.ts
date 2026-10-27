import { watchReadPermissions } from '../../../../../core/services/watch-read-permissions';
import { firstValueFrom } from 'rxjs';
import { CompanyPermissionsService } from '../../../../../core/services/company-permissions.service';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  QbeAggregate,
  QbeCondition,
  QbeField,
  QbeQuery,
  QbeResult,
  QbeService,
  QbeSource,
} from '../../../../../core/services/qbe.service';
import { PreferencesService } from '../../../../../core/services/preferences.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

const OPERATOR_LABELS: Record<string, string> = {
  igual: 'es igual a',
  distinto: 'es distinto de',
  contiene: 'contiene',
  empieza: 'empieza con',
  vacio: 'está vacío',
  no_vacio: 'no está vacío',
  mayor: 'mayor que',
  mayor_igual: 'mayor o igual que',
  menor: 'menor que',
  menor_igual: 'menor o igual que',
  entre: 'entre',
};

const AGGREGATE_LABELS: Record<QbeAggregate['funcion'], string> = {
  SUMA: 'Suma',
  CONTEO: 'Conteo',
  PROMEDIO: 'Promedio',
  MAXIMO: 'Máximo',
  MINIMO: 'Mínimo',
};

/** Consulta por ejemplo: el usuario arma su propio reporte eligiendo columnas, condiciones y totales. */
@Component({
  selector: 'app-qbe-report',
  standalone: true,
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './qbe.html',
  styleUrl: './qbe.css',
})
export class QbeReport implements OnInit {
  protected readonly permissions=inject(CompanyPermissionsService);
  private lastQuery:QbeQuery|null=null;
  private readonly readSnapshot=watchReadPermissions(() => { this.result=null;this.source=null;this.error="Los permisos cambiaron. Vuelve a abrir la consulta para cargar sus fuentes.";this.cdr.markForCheck(); });
  private readonly route = inject(ActivatedRoute);
  private readonly qbeService = inject(QbeService);
  private readonly preferences = inject(PreferencesService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly operatorLabels = OPERATOR_LABELS;
  protected readonly aggregateFunctions = Object.keys(AGGREGATE_LABELS) as QbeAggregate['funcion'][];
  protected readonly aggregateLabels = AGGREGATE_LABELS;

  protected sources: QbeSource[] = [];
  protected source: QbeSource | null = null;
  protected selectedFields = new Set<string>();
  protected conditions: QbeCondition[] = [];
  protected groupBy = new Set<string>();
  protected aggregates: QbeAggregate[] = [];
  protected orderField = '';
  protected orderDirection: 'asc' | 'desc' = 'asc';
  protected limit = 200;

  protected result: QbeResult | null = null;
  protected loading = false;
  protected error = '';

  ngOnInit(): void {
    this.qbeService.sources(this.companyId).subscribe({
      next: (sources) => {
        this.sources = sources;
        if (sources.length) this.selectSource(sources[0].codigo);
        this.cdr.detectChanges();
      },
      error: (err) => this.fail(err, 'No se pudieron cargar las fuentes de datos.'),
    });
  }

  protected get grouped(): boolean {
    return this.groupBy.size > 0;
  }

  protected get numericFields(): QbeField[] {
    return this.source?.campos.filter((c) => c.tipo === 'NUMERO') ?? [];
  }

  /** Columnas por las que se puede ordenar segun el modo (detalle o agrupado). */
  protected get orderOptions(): Array<{ codigo: string; etiqueta: string }> {
    if (!this.source) return [];
    if (!this.grouped) {
      return this.source.campos.filter((c) => this.selectedFields.has(c.codigo));
    }
    const groups = this.source.campos.filter((c) => this.groupBy.has(c.codigo));
    const totals = this.aggregates.map((a) =>
      a.funcion === 'CONTEO'
        ? { codigo: 'conteo', etiqueta: 'Cantidad de registros' }
        : {
            codigo: `${a.funcion.toLowerCase()}_${a.campo}`,
            etiqueta: `${AGGREGATE_LABELS[a.funcion]} de ${this.fieldLabel(a.campo)}`,
          },
    );
    return [...groups, ...totals];
  }

  protected selectSource(code: string): void {
    this.source = this.sources.find((s) => s.codigo === code) ?? null;
    this.selectedFields = new Set(this.source?.campos_por_defecto ?? []);
    this.conditions = [];
    this.groupBy = new Set();
    this.aggregates = [];
    this.orderField = '';
    this.result = null;
  }

  protected toggleField(code: string): void {
    if (this.selectedFields.has(code)) this.selectedFields.delete(code);
    else this.selectedFields.add(code);
  }

  protected toggleGroup(code: string): void {
    if (this.groupBy.has(code)) this.groupBy.delete(code);
    else this.groupBy.add(code);
    if (this.grouped && this.aggregates.length === 0) {
      this.aggregates = [{ campo: null, funcion: 'CONTEO' }];
    }
    this.orderField = '';
  }

  protected addCondition(): void {
    const first = this.source?.campos[0];
    if (!first) return;
    this.conditions.push({ campo: first.codigo, operador: first.operadores[0], valor: '' });
  }

  protected onConditionField(condition: QbeCondition): void {
    const field = this.field(condition.campo);
    condition.operador = field?.operadores[0] ?? 'igual';
    condition.valor = field?.tipo === 'BOOLEANO' ? 'true' : '';
    condition.valor2 = '';
  }

  protected removeCondition(index: number): void {
    this.conditions.splice(index, 1);
  }

  protected addAggregate(): void {
    this.aggregates.push({ campo: this.numericFields[0]?.codigo ?? null, funcion: this.numericFields.length ? 'SUMA' : 'CONTEO' });
  }

  protected removeAggregate(index: number): void {
    this.aggregates.splice(index, 1);
  }

  protected field(code: string | null): QbeField | undefined {
    return this.source?.campos.find((c) => c.codigo === code);
  }

  protected fieldLabel(code: string | null): string {
    return this.field(code)?.etiqueta.toLowerCase() ?? '';
  }

  protected inputType(condition: QbeCondition): string {
    const type = this.field(condition.campo)?.tipo;
    return type === 'NUMERO' ? 'number' : type === 'FECHA' ? 'date' : 'text';
  }

  protected needsValue(condition: QbeCondition): boolean {
    return condition.operador !== 'vacio' && condition.operador !== 'no_vacio';
  }

  protected run(): void {
    if (!this.source) return;
    if (!this.grouped && this.selectedFields.size === 0) {
      this.error = 'Elige al menos una columna para mostrar.';
      return;
    }
    const query: QbeQuery = {
      fuente: this.source.codigo,
      campos: this.source.campos.map((c) => c.codigo).filter((c) => this.selectedFields.has(c)),
      condiciones: this.conditions,
      agrupar: [...this.groupBy],
      agregados: this.aggregates.map((a) => ({ campo: a.funcion === 'CONTEO' ? null : a.campo, funcion: a.funcion })),
      orden: this.orderField ? { campo: this.orderField, direccion: this.orderDirection } : null,
      limite: this.limit,
    };
    this.loading = true;
    this.error = '';
    this.qbeService.run(this.companyId, query).subscribe({
      next: (result) => {
        this.lastQuery=structuredClone(query);
        this.result = result;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => this.fail(err, 'No se pudo ejecutar la consulta.'),
    });
  }

  protected format(value: unknown, type: string, code: string): string {
    if (value === null || value === undefined || value === '') return '—';
    if (type === 'BOOLEANO') return value === true || value === 'true' ? 'Sí' : 'No';
    if (type === 'FECHA') {
      const text = String(value);
      const [date, time] = text.split('T');
      const [y, m, d] = date.split('-');
      return `${d}/${m}/${y}${time ? ' ' + time.slice(0, 5) : ''}`;
    }
    if (type === 'NUMERO') {
      const isMoney = /(total|precio|valor|subtotal|descuento|credito|monto)/.test(code) && !/descuento_base/.test(code);
      const number = Number(value);
      if (isMoney) return this.preferences.formatMoney(number);
      return Number.isInteger(number) ? String(number) : number.toFixed(2);
    }
    return String(value);
  }

  protected async exportExcel(): Promise<void> {
    if(!this.lastQuery || !this.permissions.hasPermission('report:export')) return;
    try { this.result=await firstValueFrom(this.qbeService.run(this.companyId,this.lastQuery,true)); }
    catch(error) {this.result=null;this.fail(error,'No se pudo actualizar la consulta para exportar.');return;}
    if(!this.permissions.hasPermission('report:export')) return;
    const escape = (v: string) => v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const header = this.result.columnas.map((c) => `<th>${escape(c.etiqueta)}</th>`).join('');
    const rows = this.result.filas
      .map(
        (row) =>
          `<tr>${row
            .map((cell, i) => {
              const col = this.result!.columnas[i];
              let raw = col.tipo === 'NUMERO' && cell !== null ? String(cell) : this.format(cell, col.tipo, col.codigo);
              if(/^[\s\x00-\x1f]*[=+\-@]/.test(raw) && col.tipo!=='NUMERO') raw="'"+raw;
              return `<td>${escape(raw)}</td>`;
            })
            .join('')}</tr>`,
      )
      .join('');
    const html = `<!doctype html><html><head><meta charset="utf-8"></head><body>
      <h3>${escape(this.result.descripcion)}</h3>
      <table border="1"><thead><tr>${header}</tr></thead><tbody>${rows}</tbody></table></body></html>`;
    const blob = new Blob(['﻿' + html], { type: 'application/vnd.ms-excel' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `consulta-${this.source?.codigo ?? 'qbe'}.xls`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private fail(err: any, fallback: string): void {
    this.error = err?.error?.detail ?? fallback;
    this.loading = false;
    this.cdr.detectChanges();
  }
}
