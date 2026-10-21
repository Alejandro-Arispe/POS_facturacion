import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface AuditEntry {
  id_bitacora: number;
  fecha: string;
  id_usuario: number | null;
  email_usuario: string | null;
  id_empresa: number | null;
  accion: string;
  modulo: string | null;
  metodo: string | null;
  ruta: string | null;
  estado_http: number | null;
  ip: string | null;
  detalle: string | null;
  hash: string;
}

export interface AuditPage {
  items: AuditEntry[];
  total: number;
  page: number;
  size: number;
}

export interface AuditFilters {
  desde?: string;
  hasta?: string;
  accion?: string;
  modulo?: string;
  usuario?: string;
  id_empresa?: number | string;
  page?: number;
  size?: number;
}

export interface AuditVerification {
  integra: boolean;
  total_registros: number;
  registros_alterados: number[];
  mensaje: string;
}

export const AUDIT_ACTIONS = [
  'LOGIN',
  'LOGIN_FALLIDO',
  'CREAR',
  'ACTUALIZAR',
  'ELIMINAR',
  'CREAR_FALLIDO',
  'ACTUALIZAR_FALLIDO',
  'ELIMINAR_FALLIDO',
];

export const AUDIT_MODULES = [
  'AUTENTICACION',
  'EMPRESAS',
  'SUCURSALES',
  'USUARIOS',
  'ROLES',
  'CLIENTES',
  'PRODUCTOS',
  'INVENTARIO',
  'VENTAS',
  'PEDIDOS',
  'CAJAS',
  'REPORTES',
  'CONFIGURACION',
  'SUSCRIPCIONES',
  'SUPERADMIN',
];

export function buildQuery(filters: AuditFilters): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.set(key, String(value).trim());
    }
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly api = inject(ApiService);

  listCompanyLog(companyId: string | number, filters: AuditFilters): Observable<AuditPage> {
    return this.api.get<AuditPage>(`/api/empresas/${companyId}/bitacora${buildQuery(filters)}`);
  }
}
