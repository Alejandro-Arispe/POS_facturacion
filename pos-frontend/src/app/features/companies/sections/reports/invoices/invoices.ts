import { watchReadPermissions } from '../../../../../core/services/watch-read-permissions';
import { CompanyPermissionsService } from '../../../../../core/services/company-permissions.service';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MoneyPipe } from '../../../../../shared/pipes/money.pipe';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiService } from '../../../../../core/services/api.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';
import { InvoicePrintService } from '../../../../../core/services/invoice-print.service';
import { SiatService, SinMotivoAnulacion } from '../../../../../core/services/siat.service';

interface CompanyInvoice {
  id_factura: number;
  id_venta: number;
  numero_factura: number;
  fecha_emision: string;
  nit_cliente: string;
  nombre_cliente: string;
  monto_total: number;
  cuf: string;
  estado_siat: 'EMITIDA' | 'ANULADA' | 'CONTINGENCIA';
}

interface ResendInvoiceResponse {
  mensaje: string;
  id_factura: number;
  correo_cliente: string;
  enviado: boolean;
}

@Component({
  selector: 'app-invoices-report',
  imports: [MoneyPipe, Navbar, Sidebar, FormsModule],
  templateUrl: './invoices.html',
  styleUrl: './invoices.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoicesReport implements OnInit {
  protected readonly permissions=inject(CompanyPermissionsService);
  private readonly readSnapshot=watchReadPermissions(() => { this.invoices=[];this.cdr.markForCheck(); });
  private readonly route = inject(ActivatedRoute);
  private readonly apiService = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected invoices: CompanyInvoice[] = [];
  protected loading = false;
  protected error = '';
  protected successMessage = '';
  protected resendingInvoiceId: number | null = null;
  private readonly siat = inject(SiatService);
  private readonly invoicePrint = inject(InvoicePrintService);
  // Modal de anulación SIAT
  protected motivos: SinMotivoAnulacion[] = [];
  protected annulTarget: CompanyInvoice | null = null;
  protected annulReason: number | null = null;
  protected annulling = false;

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

  protected downloadInvoice(invoice:CompanyInvoice, format:'xml'|'pdf'):void {
    if(!this.permissions.hasPermission('invoice:reprint')) return;
    this.apiService.getBlob(`/api/facturas/${invoice.id_factura}/${format}`).subscribe({
      next:(blob) => {const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`factura-${invoice.id_factura}.${format}`;link.click();setTimeout(() => URL.revokeObjectURL(url),1000);},
      error:(error) => {this.error=error?.error?.detail??'No se pudo descargar la factura.';this.cdr.detectChanges();}
    });
  }

  protected printInvoice(invoice: CompanyInvoice): void {
    this.invoicePrint.print(invoice.id_factura).catch((error) => {
      this.error = error?.error?.detail ?? error?.message ?? 'No se pudo generar la factura.';
      this.cdr.detectChanges();
    });
  }

  protected openAnnul(invoice: CompanyInvoice): void {
    this.error = '';
    this.successMessage = '';
    this.annulTarget = invoice;
    this.annulReason = null;
    if (!this.motivos.length)
      this.siat.motivosAnulacion().subscribe({
        next: (motivos) => {
          this.motivos = motivos;
          this.cdr.detectChanges();
        },
        error: () => {
          this.error = 'No se pudieron cargar los motivos de anulación.';
          this.cdr.detectChanges();
        },
      });
  }

  protected closeAnnul(): void {
    if (!this.annulling) this.annulTarget = null;
  }

  protected confirmAnnul(): void {
    const invoice = this.annulTarget;
    if (!invoice || !this.annulReason || this.annulling) return;
    this.annulling = true;
    this.siat
      .anular(invoice.id_factura, this.annulReason)
      .pipe(
        finalize(() => {
          this.annulling = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (response) => {
          invoice.estado_siat = 'ANULADA';
          this.successMessage = response.mensaje;
          this.annulTarget = null;
        },
        error: (error) => {
          this.error = error?.error?.detail ?? 'No se pudo anular la factura.';
          this.annulTarget = null;
        },
      });
  }

  protected shortCuf(cuf: string): string {
    return cuf && cuf.length > 16 ? `${cuf.slice(0, 8)}…${cuf.slice(-8)}` : cuf;
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
