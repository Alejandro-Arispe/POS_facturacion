import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuditEntry, AuditFilters, AuditService } from '../../../../core/services/audit.service';
import { AuditTable } from '../../../../shared/components/audit-table/audit-table';
import { Navbar } from '../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../shared/components/sidebar/sidebar';

interface EntradaBitacora extends AuditEntry {
  hora_cliente?: string | null;
  hora_servidor?: string | null;
  recurso?: string | null;
  id_recurso?: string | null;
}

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [Navbar, Sidebar, AuditTable],
  templateUrl: './audit-log.html',
  styleUrl: './audit-log.css',
})
export class AuditLog implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auditService = inject(AuditService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected filters: AuditFilters = { page: 0, size: 25 };
  protected items: EntradaBitacora[] = [];
  protected total = 0;
  protected loading = false;
  protected error = '';

  ngOnInit(): void {
    this.load();
  }

  protected onFiltersChange(filters: AuditFilters): void {
    this.filters = filters;
    this.load();
  }

  protected formatHora(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/.exec(value);
    if (!match) {
      return value;
    }
    return `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}:${match[6]}`;
  }

  private load(): void {
    this.loading = true;
    this.error = '';
    this.auditService.listCompanyLog(this.companyId, this.filters).subscribe({
      next: (page) => {
        this.items = page.items as EntradaBitacora[];
        this.total = page.total;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = err?.error?.detail ?? 'No se pudo cargar la bitácora.';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }
}
