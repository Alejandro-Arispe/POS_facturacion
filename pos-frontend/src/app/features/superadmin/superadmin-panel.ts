import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Navbar } from '../../shared/components/navbar/navbar';
import { AuditTable } from '../../shared/components/audit-table/audit-table';
import { AuditEntry, AuditFilters, AuditVerification } from '../../core/services/audit.service';
import {
  SuperAdminCompany,
  SuperAdminModule,
  SuperAdminPlan,
  SuperAdminPlanPayload,
  SuperAdminService,
  SuperAdminSummary,
  SuperAdminUser,
} from '../../core/services/superadmin.service';

type SuperAdminTab = 'summary' | 'companies' | 'plans' | 'users' | 'audit';

interface PlanForm {
  id_plan: number | null;
  nombre: string;
  descripcion: string;
  precio: number;
  modulos: Set<string>;
  configuracion: string;
}

@Component({
  selector: 'app-superadmin-panel',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, CurrencyPipe, Navbar, AuditTable],
  templateUrl: './superadmin-panel.html',
  styleUrl: './superadmin-panel.css',
})
export class SuperAdminPanel implements OnInit {
  private readonly service = inject(SuperAdminService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected activeTab: SuperAdminTab = 'summary';
  protected message = '';
  protected error = '';

  protected summary: SuperAdminSummary | null = null;
  protected companies: SuperAdminCompany[] = [];
  protected plans: SuperAdminPlan[] = [];
  protected modules: SuperAdminModule[] = [];
  protected users: SuperAdminUser[] = [];
  protected companySearch = '';
  protected userSearch = '';

  protected assigningCompany: SuperAdminCompany | null = null;
  protected assignPlanId: number | null = null;
  protected assignMonths = 1;

  protected planForm: PlanForm | null = null;

  protected auditFilters: AuditFilters = { page: 0, size: 25 };
  protected auditItems: AuditEntry[] = [];
  protected auditTotal = 0;
  protected auditLoading = false;
  protected verification: AuditVerification | null = null;
  protected verifying = false;

  ngOnInit(): void {
    this.loadSummary();
    this.loadPlans();
  }

  protected setTab(tab: SuperAdminTab): void {
    this.activeTab = tab;
    this.message = '';
    this.error = '';
    if (tab === 'summary') this.loadSummary();
    if (tab === 'companies') this.loadCompanies();
    if (tab === 'plans') this.loadPlans();
    if (tab === 'users') this.loadUsers();
    if (tab === 'audit') this.loadAudit();
  }

  // ---------- Resumen ----------
  private loadSummary(): void {
    this.run(this.service.summary(), (data) => (this.summary = data));
  }

  // ---------- Empresas ----------
  protected get filteredCompanies(): SuperAdminCompany[] {
    const term = this.companySearch.trim().toLowerCase();
    if (!term) return this.companies;
    return this.companies.filter((c) =>
      [c.nombre, c.nit, c.correo, ...c.administradores].some((v) => v?.toLowerCase().includes(term)),
    );
  }

  private loadCompanies(): void {
    this.run(this.service.companies(), (data) => (this.companies = data));
  }

  protected toggleCompany(company: SuperAdminCompany): void {
    const next = !company.activo;
    const verb = next ? 'reactivar' : 'suspender';
    if (!confirm(`¿Seguro que deseas ${verb} la empresa "${company.nombre}"?`)) return;
    this.run(this.service.setCompanyActive(company.id_empresa, next), (updated) => {
      this.replaceCompany(updated);
      this.message = `Empresa ${next ? 'reactivada' : 'suspendida'}.`;
    });
  }

  protected openAssign(company: SuperAdminCompany): void {
    this.assigningCompany = company;
    this.assignPlanId = company.suscripcion_activa?.id_plan ?? this.plans[0]?.id_plan ?? null;
    this.assignMonths = 1;
  }

  protected confirmAssign(): void {
    if (!this.assigningCompany || !this.assignPlanId) return;
    const company = this.assigningCompany;
    this.run(this.service.assignPlan(company.id_empresa, this.assignPlanId, this.assignMonths), (updated) => {
      this.replaceCompany(updated);
      this.assigningCompany = null;
      this.message = `Plan asignado a ${updated.nombre}.`;
    });
  }

  protected cancelPlan(company: SuperAdminCompany): void {
    if (!confirm(`¿Cancelar la suscripción de "${company.nombre}"? La empresa quedará sin plan.`)) return;
    this.run(this.service.cancelPlan(company.id_empresa), (updated) => {
      this.replaceCompany(updated);
      this.message = 'Suscripción cancelada.';
    });
  }

  private replaceCompany(updated: SuperAdminCompany): void {
    this.companies = this.companies.map((c) => (c.id_empresa === updated.id_empresa ? updated : c));
  }

  // ---------- Planes ----------
  private loadPlans(): void {
    this.run(this.service.plans(), (data) => (this.plans = data));
    if (this.modules.length === 0) {
      this.run(this.service.modules(), (data) => (this.modules = data));
    }
  }

  protected newPlan(): void {
    this.planForm = { id_plan: null, nombre: '', descripcion: '', precio: 0, modulos: new Set(), configuracion: '' };
  }

  protected editPlan(plan: SuperAdminPlan): void {
    this.planForm = {
      id_plan: plan.id_plan,
      nombre: plan.nombre,
      descripcion: plan.descripcion,
      precio: Number(plan.precio),
      modulos: new Set(plan.modulos.map((m) => m.codigo)),
      configuracion: plan.modulos.find((m) => m.configuracion)?.configuracion ?? '',
    };
  }

  protected toggleModule(code: string): void {
    if (!this.planForm) return;
    if (this.planForm.modulos.has(code)) this.planForm.modulos.delete(code);
    else this.planForm.modulos.add(code);
  }

  protected savePlan(): void {
    if (!this.planForm) return;
    const form = this.planForm;
    const payload: SuperAdminPlanPayload = {
      nombre: form.nombre,
      descripcion: form.descripcion,
      precio: Number(form.precio),
      modulos: [...form.modulos],
      configuracion: form.configuracion || null,
    };
    const request = form.id_plan ? this.service.updatePlan(form.id_plan, payload) : this.service.createPlan(payload);
    this.run(request, () => {
      this.planForm = null;
      this.message = form.id_plan ? 'Plan actualizado.' : 'Plan creado.';
      this.loadPlans();
    });
  }

  // ---------- Usuarios ----------
  protected get filteredUsers(): SuperAdminUser[] {
    const term = this.userSearch.trim().toLowerCase();
    if (!term) return this.users;
    return this.users.filter((u) =>
      [u.email, u.nombre_completo ?? '', ...u.empresas].some((v) => v.toLowerCase().includes(term)),
    );
  }

  private loadUsers(): void {
    this.run(this.service.users(), (data) => (this.users = data));
  }

  protected toggleUser(user: SuperAdminUser): void {
    const next = !user.activo;
    if (!confirm(`¿${next ? 'Activar' : 'Bloquear'} la cuenta ${user.email}?`)) return;
    this.run(this.service.setUserActive(user.id_usuario, next), (updated) => {
      this.users = this.users.map((u) => (u.id_usuario === updated.id_usuario ? updated : u));
      this.message = `Cuenta ${next ? 'activada' : 'bloqueada'}.`;
    });
  }

  // ---------- Bitácora ----------
  protected onAuditFilters(filters: AuditFilters): void {
    this.auditFilters = filters;
    this.loadAudit();
  }

  private loadAudit(): void {
    this.auditLoading = true;
    this.run(this.service.auditLog(this.auditFilters), (page) => {
      this.auditItems = page.items;
      this.auditTotal = page.total;
      this.auditLoading = false;
    });
  }

  protected verifyIntegrity(): void {
    this.verifying = true;
    this.run(this.service.verifyAuditLog(), (result) => {
      this.verification = result;
      this.verifying = false;
    });
  }

  // ---------- util ----------
  private run<T>(request: Observable<T>, onSuccess: (data: T) => void): void {
    this.error = '';
    request.subscribe({
      next: (data) => {
        onSuccess(data);
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = err?.error?.detail ?? 'Ocurrió un error al comunicarse con el servidor.';
        this.auditLoading = false;
        this.verifying = false;
        this.cdr.detectChanges();
      },
    });
  }
}
