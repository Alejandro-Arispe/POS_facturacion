import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MoneyPipe } from '../../../../../shared/pipes/money.pipe';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiService } from '../../../../../core/services/api.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

interface CompanyInvoice {
  id_factura: number;
  id_venta: number;
  fecha_emision: string;
  nombre_cliente: string;
  monto_total: number;
  xml_generado: string;
  pdf_generado: string;
}

interface ResendInvoiceResponse {
  mensaje: string;
  id_factura: number;
  correo_cliente: string;
  enviado: boolean;
}

@Component({
  selector: 'app-invoices-report',
  imports: [MoneyPipe, Navbar, Sidebar],
  templateUrl: './invoices.html',
  styleUrl: './invoices.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoicesReport implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly apiService = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected invoices: CompanyInvoice[] = [];
  protected loading = false;
  protected error = '';
  protected successMessage = '';
  protected resendingInvoiceId: number | null = null;

  ngOnInit(): void {
    this.loadInvoices();
  }

  protected formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? value
      : new Intl.DateTimeFormat('es-BO', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        }).format(date);
  }

  protected formatCurrency(value: number): string {
    return new Intl.NumberFormat('es-BO', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(value) || 0);
  }

  protected fileUrl(path: string): string {
    if (/^https?:\/\//i.test(path)) {
      return path;
    }

    const baseUrl = this.apiService.getBaseUrl().replace(/\/+$/, '');
    const normalizedPath = path.replace(/\\/g, '/').replace(/^\/+/, '');
    return `${baseUrl}/${normalizedPath}`;
  }

  protected trackInvoice(_index: number, invoice: CompanyInvoice): number {
    return invoice.id_factura;
  }

  protected resendInvoice(invoice: CompanyInvoice): void {
    if (this.resendingInvoiceId !== null) {
      return;
    }

    this.error = '';
    this.successMessage = '';
    this.resendingInvoiceId = invoice.id_factura;

    this.apiService
      .post<ResendInvoiceResponse, { id_factura: number }>(
        `/api/empresas/${this.companyId}/facturas/reenviar`,
        { id_factura: invoice.id_factura },
      )
      .pipe(
        finalize(() => {
          this.resendingInvoiceId = null;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (response) => {
          this.successMessage = response.correo_cliente
            ? `${response.mensaje} Correo: ${response.correo_cliente}`
            : response.mensaje;
        },
        error: (error) => {
          this.error = error?.error?.detail ?? 'No se pudo reenviar la factura.';
        },
      });
  }

  private loadInvoices(): void {
    if (!this.companyId) {
      this.error = 'No se encontro la empresa para cargar las facturas.';
      return;
    }

    this.loading = true;
    this.error = '';

    this.apiService
      .get<CompanyInvoice[]>(`/api/empresas/${this.companyId}/facturas`)
      .pipe(
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (invoices) => {
          this.invoices = Array.isArray(invoices) ? invoices : [];
        },
        error: (error) => {
          this.invoices = [];
          this.error = error?.error?.detail ?? 'No se pudieron cargar las facturas.';
        },
      });
  }
}
