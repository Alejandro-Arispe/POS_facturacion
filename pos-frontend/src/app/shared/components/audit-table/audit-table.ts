import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AUDIT_ACTIONS,
  AUDIT_MODULES,
  AuditEntry,
  AuditFilters,
} from '../../../core/services/audit.service';

/** Tabla de bitacora con filtros y paginacion, compartida por la vista de empresa y la del SuperAdmin. */
@Component({
  selector: 'app-audit-table',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './audit-table.html',
  styleUrl: './audit-table.css',
})
export class AuditTable {
  @Input() items: AuditEntry[] = [];
  @Input() total = 0;
  @Input() loading = false;
  @Input() showCompany = false;
  @Input() filters: AuditFilters = { page: 0, size: 25 };
  @Output() filtersChange = new EventEmitter<AuditFilters>();

  protected readonly actions = AUDIT_ACTIONS;
  protected readonly modules = AUDIT_MODULES;
  protected selected: AuditEntry | null = null;

  get page(): number {
    return this.filters.page ?? 0;
  }

  get size(): number {
    return this.filters.size ?? 25;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.total / this.size));
  }

  protected apply(): void {
    this.filtersChange.emit({ ...this.filters, page: 0 });
  }

  protected clear(): void {
    this.filtersChange.emit({ page: 0, size: this.size });
  }

  protected goTo(page: number): void {
    if (page < 0 || page >= this.totalPages) {
      return;
    }
    this.filtersChange.emit({ ...this.filters, page });
  }

  protected badgeClass(action: string): string {
    if (action.endsWith('FALLIDO')) {
      return 'badge-danger';
    }
    if (action === 'LOGIN') {
      return 'badge-info';
    }
    if (action === 'ELIMINAR') {
      return 'badge-warning';
    }
    return 'badge-success';
  }
}
