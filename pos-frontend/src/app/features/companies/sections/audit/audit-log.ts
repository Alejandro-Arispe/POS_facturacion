import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuditEntry, AuditFilters, AuditService } from '../../../../core/services/audit.service';
import { Navbar } from '../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../shared/components/sidebar/sidebar';
import { AuditTable } from '../../../../shared/components/audit-table/audit-table';

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
  protected items: AuditEntry[] = [];
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

  private load(): void {
    this.loading = true;
    this.error = '';
    this.auditService.listCompanyLog(this.companyId, this.filters).subscribe({
      next: (page) => {
        this.items = page.items;
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
