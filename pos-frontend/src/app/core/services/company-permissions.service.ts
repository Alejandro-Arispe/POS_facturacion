import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import {
  Observable,
  Subscription,
  catchError,
  exhaustMap,
  fromEvent,
  map,
  merge,
  of,
  tap,
  timer,
} from 'rxjs';

import { ApiService } from './api.service';
import { PolicyRevisionService } from './policy-revision.service';

export const COMPANY_PERMISSION_CODES = [
  'company:update',
  'branch:read',
  'branch:create',
  'branch:update',
  'user:read',
  'user:create',
  'user:update',
  'role:read',
  'role:create',
  'role:update',
  'client:read',
  'client:create',
  'client:update',
  'client_category:read',
  'client_category:create',
  'client_category:update',
  'product:read',
  'product:create',
  'product:update',
  'inventory:read',
  'inventory:configure',
  'inventory_movement:read',
  'inventory_movement:create',
  'inventory_alert:read',
  'cash_register:read',
  'cash_register:update',
  'cash_register:create',
  'cash_register:open',
  'cash_register:close',
  'order:read',
  'order:manage',
  'sale:read',
  'sale:create',
  'sale:void',
  'sale:discount',
  'invoice:issue',
  'invoice:reprint',
  'report:generate',
  'report:export',
  'dashboard:read',
  'audit:read',
] as const;

export type CompanyPermissionCode = (typeof COMPANY_PERMISSION_CODES)[number];
export type CompanyPermissionsByCode = Record<CompanyPermissionCode, boolean> &
  Record<string, boolean>;

export type EstadoCampoProducto = 'EDITABLE' | 'SOLO_LECTURA' | 'OCULTO';
export type CampoProducto = 'costo' | 'precio' | 'editar' | 'eliminar';
export type CamposProducto = Record<CampoProducto, EstadoCampoProducto>;

export interface CampoPrivilegio {
  campo: CampoProducto;
  estado: EstadoCampoProducto;
}

export const CAMPOS_PRODUCTO: readonly CampoProducto[] = ['costo', 'precio', 'editar', 'eliminar'];

export function camposProductoPorDefecto(): CamposProducto {
  return {
    costo: 'OCULTO',
    precio: 'OCULTO',
    editar: 'OCULTO',
    eliminar: 'OCULTO',
  };
}

export interface CompanyPermissionModule {
  id_modulo: number;
  codigo: string;
  nombre: string;
}

export interface CompanyPermission {
  puede_delegar?: boolean;
  id_permiso: number;
  codigo: string;
  codigo_canonico?: string;
  nombre: string;
  id_modulo: number;
  modulo: CompanyPermissionModule;
  activo_rol_permiso: boolean;
}

export interface CompanyPlanModule {
  id_plan_modulo: number;
  id_plan: number;
  id_modulo: number;
  configuracion: string | null;
  modulo: CompanyPermissionModule;
}

export interface CompanySubscriptionPlan {
  limites?: { codigo: string; reportes: string; pedidos: boolean; predicciones: boolean };
  id_plan: number;
  nombre: string;
  descripcion: string;
  precio: number | string;
  plan_modulos: CompanyPlanModule[];
}

export interface CompanyActiveSubscription {
  id_historial_suscripcion: number;
  id_empresa: number;
  id_plan: number;
  fecha_inicio: string;
  fecha_fin: string;
  estado: string;
  plan: CompanySubscriptionPlan;
}

export interface CompanyPlanConfiguration {
  reportes: string;
}

const STORAGE_KEY = 'company_permissions_by_code';
const SUBSCRIPTION_STORAGE_KEY = 'company_active_subscription';
const PLAN_CONFIGURATION_STORAGE_KEY = 'company_plan_configuration';

const EMPTY_PLAN_CONFIGURATION: CompanyPlanConfiguration = {
  reportes: '',
};

export const EMPTY_COMPANY_PERMISSIONS: CompanyPermissionsByCode = COMPANY_PERMISSION_CODES.reduce(
  (permissions, code) => {
    permissions[code] = false;
    return permissions;
  },
  {} as CompanyPermissionsByCode,
);

@Injectable({
  providedIn: 'root',
})
export class CompanyPermissionsService {
  private readonly apiService = inject(ApiService);
  private readonly policyRevision = inject(PolicyRevisionService);
  private readonly destroyRef = inject(DestroyRef);
  constructor() {
    this.destroyRef.onDestroy(() => this.contextWatch?.unsubscribe());
  }
  private readonly permissionsSignal = signal<CompanyPermissionsByCode>(
    this.createEmptyPermissions(),
  );
  private readonly camposProductoSignal = signal<CamposProducto>(camposProductoPorDefecto());
  private readonly activeSubscriptionSignal = signal<CompanyActiveSubscription | null>(null);
  private readonly planConfigurationSignal = signal<CompanyPlanConfiguration>({
    ...EMPTY_PLAN_CONFIGURATION,
  });

  readonly permissions = this.permissionsSignal.asReadonly();
  readonly camposProducto = this.camposProductoSignal.asReadonly();
  readonly activeSubscription = this.activeSubscriptionSignal.asReadonly();
  readonly planConfiguration = this.planConfigurationSignal.asReadonly();

  private contextWatch?: Subscription;
  readonly contextError = signal(false);
  private readonly delegableSignal = signal<ReadonlySet<number>>(new Set());
  canDelegate(id: number): boolean {
    return this.delegableSignal().has(id);
  }

  hasField(resource: string, action: string, field: string): boolean {
    return (
      this.hasPermission(`${resource}:${action}`) &&
      this.hasPermission(`${resource}:${action}:${field}`)
    );
  }

  /** Poll only while a company context is open; also refresh when returning to the window. */
  watchContext(company: string | number, branch?: string | number | null): void {
    this.contextWatch?.unsubscribe();
    this.policyRevision.update("scope", `${company}:${branch ?? ""}`);
    this.contextError.set(false);
    const url = `/api/empresas/${company}/mis-permisos${branch ? `?id_sucursal=${encodeURIComponent(branch)}` : ''}`;
    this.contextWatch = merge(timer(30000, 30000), fromEvent(window, 'focus'))
      .pipe(
        exhaustMap(() =>
          this.apiService
            .get<{
              permisos: CompanyPermission[];
              suscripcion_activa: CompanyActiveSubscription | null;
            }>(url)
            .pipe(catchError(() => of(null))),
        ),
      )
      .subscribe((response) => {
        if (
          !response ||
          !response.suscripcion_activa ||
          !['activo', 'activa'].includes(response.suscripcion_activa.estado.toLowerCase())
        ) {
          this.savePermissions([]);
          this.saveActiveSubscription(null);
          this.delegableSignal.set(new Set());
          this.camposProductoSignal.set(camposProductoPorDefecto());
          this.contextError.set(true);
          return;
        }
        this.savePermissions(response.permisos);
        this.saveActiveSubscription(response.suscripcion_activa);
        this.contextError.set(false);
      });
  }

  hasPermission(permission: string): boolean {
    const permissions = this.permissionsSignal();
    return permissions[permission] === true;
  }

  savePermissions(permissions: CompanyPermission[]): CompanyPermissionsByCode {
    const permissionsByCode = this.createEmptyPermissions();
    this.delegableSignal.set(
      new Set(
        (permissions ?? [])
          .filter((p) => p.activo_rol_permiso && p.puede_delegar)
          .map((p) => p.id_permiso),
      ),
    );

    if (permissions && permissions.length > 0) {
      for (const permission of permissions) {
        const code = permission.codigo_canonico ?? permission.codigo;
        if (/^[a-z][a-z0-9_]*:[a-z][a-z0-9_]*(?::[a-z][a-z0-9_]*)?$/.test(code)) {
          permissionsByCode[code] ||= permission.activo_rol_permiso === true;
        }
      }
    }

    this.policyRevision.update("permissions", Object.keys(permissionsByCode).filter((code) => permissionsByCode[code]).sort().join("|"));
    this.permissionsSignal.set(permissionsByCode);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(permissionsByCode));

    return permissionsByCode;
  }

  saveActiveSubscription(subscription: CompanyActiveSubscription | null): CompanyPlanConfiguration {
    this.policyRevision.update("subscription", JSON.stringify(subscription));
    this.activeSubscriptionSignal.set(subscription);

    if (subscription) {
      localStorage.setItem(SUBSCRIPTION_STORAGE_KEY, JSON.stringify(subscription));
    } else {
      localStorage.removeItem(SUBSCRIPTION_STORAGE_KEY);
    }

    const planConfiguration = this.parsePlanConfiguration(subscription);
    this.planConfigurationSignal.set(planConfiguration);
    localStorage.setItem(PLAN_CONFIGURATION_STORAGE_KEY, JSON.stringify(planConfiguration));
    return planConfiguration;
  }

  getPermissionCodes(): string[] {
    return Object.entries(this.permissionsSignal())
      .filter(([, enabled]) => enabled === true)
      .map(([code]) => code);
  }

  clearPermissions(): void {
    this.policyRevision.reset();
    this.contextWatch?.unsubscribe();
    this.contextError.set(false);
    this.delegableSignal.set(new Set());
    const emptyPermissions = this.createEmptyPermissions();
    this.permissionsSignal.set(emptyPermissions);
    this.activeSubscriptionSignal.set(null);
    this.planConfigurationSignal.set({ ...EMPTY_PLAN_CONFIGURATION });
    this.camposProductoSignal.set(camposProductoPorDefecto());
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(SUBSCRIPTION_STORAGE_KEY);
    localStorage.removeItem(PLAN_CONFIGURATION_STORAGE_KEY);
  }

  private createEmptyPermissions(): CompanyPermissionsByCode {
    return { ...EMPTY_COMPANY_PERMISSIONS };
  }

  private parsePlanConfiguration(
    subscription: CompanyActiveSubscription | null,
  ): CompanyPlanConfiguration {
    const planConfiguration = { ...EMPTY_PLAN_CONFIGURATION };
    const planModules = subscription?.plan?.plan_modulos ?? [];

    for (const planModule of planModules) {
      const configurationLines = planModule.configuracion?.split(/\r?\n/) ?? [];

      for (const line of configurationLines) {
        const separatorIndex = line.indexOf('=');

        if (separatorIndex === -1) {
          continue;
        }

        const key = line.slice(0, separatorIndex).trim();
        const value = line.slice(separatorIndex + 1).trim();

        if (this.isPlanConfigurationKey(key)) {
          planConfiguration[key] = value;
        }
      }
    }

    return planConfiguration;
  }

  private isPlanConfigurationKey(key: string): key is keyof CompanyPlanConfiguration {
    return key === 'reportes';
  }

  private readStoredSubscription(): CompanyActiveSubscription | null {
    const storedSubscription = localStorage.getItem(SUBSCRIPTION_STORAGE_KEY);

    if (!storedSubscription) {
      return null;
    }

    try {
      return JSON.parse(storedSubscription) as CompanyActiveSubscription;
    } catch {
      return null;
    }
  }

  private readStoredPlanConfiguration(): CompanyPlanConfiguration {
    const storedPlanConfiguration = localStorage.getItem(PLAN_CONFIGURATION_STORAGE_KEY);

    if (!storedPlanConfiguration) {
      return { ...EMPTY_PLAN_CONFIGURATION };
    }

    try {
      const parsedConfiguration = JSON.parse(
        storedPlanConfiguration,
      ) as Partial<CompanyPlanConfiguration>;

      return {
        reportes: String(parsedConfiguration.reportes ?? ''),
      };
    } catch {
      return { ...EMPTY_PLAN_CONFIGURATION };
    }
  }
}
