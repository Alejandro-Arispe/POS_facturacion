import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { AuditFilters, AuditPage, AuditVerification, buildQuery } from './audit.service';

export interface SuperAdminSummary {
  total_empresas: number;
  empresas_activas: number;
  empresas_con_plan: number;
  total_usuarios: number;
  usuarios_activos: number;
  ingresos_mensuales: number;
  eventos_bitacora_hoy: number;
  eventos_bitacora_total: number;
}

export interface SuperAdminSubscription {
  id_historial_suscripcion: number;
  id_plan: number;
  plan_nombre: string;
  estado: string;
  fecha_inicio: string;
  fecha_fin: string | null;
}

export interface SuperAdminCompany {
  id_empresa: number;
  nombre: string;
  razon_social: string;
  nit: string;
  correo: string;
  fecha_creacion: string;
  activo: boolean;
  administradores: string[];
  sucursales: number;
  suscripcion_activa: SuperAdminSubscription | null;
}

export interface SuperAdminPlanModule {
  id_modulo: number;
  codigo: string;
  nombre: string;
  configuracion: string | null;
}

export interface SuperAdminPlan {
  id_plan: number;
  nombre: string;
  descripcion: string;
  precio: number;
  modulos: SuperAdminPlanModule[];
  empresas_suscritas: number;
}

export interface SuperAdminModule {
  id_modulo: number;
  codigo: string;
  nombre: string;
}

export interface SuperAdminPlanPayload {
  nombre: string;
  descripcion: string;
  precio: number;
  modulos: string[];
  configuracion?: string | null;
}

export interface SuperAdminUser {
  id_usuario: number;
  email: string;
  nombre_completo: string | null;
  fecha_creacion: string;
  activo: boolean;
  super_admin: boolean;
  empresas: string[];
}

@Injectable({ providedIn: 'root' })
export class SuperAdminService {
  private readonly api = inject(ApiService);

  summary(): Observable<SuperAdminSummary> {
    return this.api.get<SuperAdminSummary>('/api/superadmin/resumen');
  }

  companies(): Observable<SuperAdminCompany[]> {
    return this.api.get<SuperAdminCompany[]>('/api/superadmin/empresas');
  }

  setCompanyActive(id: number, activo: boolean): Observable<SuperAdminCompany> {
    return this.api.put<SuperAdminCompany, { activo: boolean }>(`/api/superadmin/empresas/${id}/estado`, { activo });
  }

  assignPlan(id: number, idPlan: number, meses: number): Observable<SuperAdminCompany> {
    return this.api.post<SuperAdminCompany, { id_plan: number; meses: number }>(
      `/api/superadmin/empresas/${id}/suscripcion`,
      { id_plan: idPlan, meses },
    );
  }

  cancelPlan(id: number): Observable<SuperAdminCompany> {
    return this.api.delete<SuperAdminCompany>(`/api/superadmin/empresas/${id}/suscripcion`);
  }

  plans(): Observable<SuperAdminPlan[]> {
    return this.api.get<SuperAdminPlan[]>('/api/superadmin/planes');
  }

  modules(): Observable<SuperAdminModule[]> {
    return this.api.get<SuperAdminModule[]>('/api/superadmin/modulos');
  }

  createPlan(payload: SuperAdminPlanPayload): Observable<SuperAdminPlan> {
    return this.api.post<SuperAdminPlan, SuperAdminPlanPayload>('/api/superadmin/planes', payload);
  }

  updatePlan(id: number, payload: SuperAdminPlanPayload): Observable<SuperAdminPlan> {
    return this.api.put<SuperAdminPlan, SuperAdminPlanPayload>(`/api/superadmin/planes/${id}`, payload);
  }

  users(): Observable<SuperAdminUser[]> {
    return this.api.get<SuperAdminUser[]>('/api/superadmin/usuarios');
  }

  setUserActive(id: number, activo: boolean): Observable<SuperAdminUser> {
    return this.api.put<SuperAdminUser, { activo: boolean }>(`/api/superadmin/usuarios/${id}/estado`, { activo });
  }

  auditLog(filters: AuditFilters): Observable<AuditPage> {
    return this.api.get<AuditPage>(`/api/superadmin/bitacora${buildQuery(filters)}`);
  }

  verifyAuditLog(): Observable<AuditVerification> {
    return this.api.get<AuditVerification>('/api/superadmin/bitacora/verificar');
  }
}
