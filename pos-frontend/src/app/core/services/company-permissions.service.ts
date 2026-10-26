import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { ApiService } from './api.service';

export const COMPANY_PERMISSION_CODES = [
  'EMPRESA_EDITAR',
  'SUCURSAL_VER',
  'SUCURSAL_CREAR',
  'SUCURSAL_EDITAR',
  'USUARIO_VER',
  'USUARIO_CREAR',
  'USUARIO_EDITAR',
  'ROL_VER',
  'ROL_CREAR',
  'ROL_EDITAR',
  'CLIENTE_VER',
  'CLIENTE_CREAR',
  'CLIENTE_EDITAR',
  'CATEGORIA_VER',
  'CATEGORIA_CREAR',
  'CATEGORIA_EDITAR',
  'PRODUCTO_VER',
  'PRODUCTO_CREAR',
  'PRODUCTO_EDITAR',
  'STOCK_VER',
  'STOCK_CONFIGURAR',
  'MOVIMIENTO_VER',
  'MOVIMIENTO_REGISTRAR',
  'ALERTA_VER',
  'CAJA_VER',
  'CAJA_EDITAR',
  'CAJA_CREAR',
  'CAJA_ABRIR',
  'CAJA_CERRAR',
  'PEDIDO_VER',
  'PEDIDO_GESTIONAR',
  'VENTA_VER',
  'VENTA_CREAR',
  'VENTA_ANULAR',
  'VENTA_DESCUENTO',
  'FACTURA_EMITIR',
  'FACTURA_REIMPRIMIR',
  'REPORTE_GENERAR',
  'REPORTE_EXPORTAR',
  'DASHBOARD_VER',
  'BITACORA_VER',
] as const;

export type CompanyPermissionCode = (typeof COMPANY_PERMISSION_CODES)[number];
export type CompanyPermissionsByCode = Record<CompanyPermissionCode, boolean>;

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
    costo: 'EDITABLE',
    precio: 'EDITABLE',
    editar: 'EDITABLE',
    eliminar: 'EDITABLE',
  };
}

export interface CompanyPermissionModule {
  id_modulo: number;
  codigo: string;
  nombre: string;
}

export interface CompanyPermission {
  id_permiso: number;
  codigo: string;
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

const PERMISSION_CODE_ALIASES: Partial<Record<string, CompanyPermissionCode>> = {
  'empresa.editar': 'EMPRESA_EDITAR',
  'sucursales.ver': 'SUCURSAL_VER',
  'sucursales.crear': 'SUCURSAL_CREAR',
  'sucursales.editar': 'SUCURSAL_EDITAR',
  'usuarios.ver': 'USUARIO_VER',
  'usuarios.crear': 'USUARIO_CREAR',
  'usuarios.editar': 'USUARIO_EDITAR',
  'roles.ver': 'ROL_VER',
  'roles.crear': 'ROL_CREAR',
  'roles.editar': 'ROL_EDITAR',
  'clientes.ver': 'CLIENTE_VER',
  'clientes.crear': 'CLIENTE_CREAR',
  'clientes.editar': 'CLIENTE_EDITAR',
  'categorias.ver': 'CATEGORIA_VER',
  'categorias.crear': 'CATEGORIA_CREAR',
  'categorias.editar': 'CATEGORIA_EDITAR',
  'productos.ver': 'PRODUCTO_VER',
  'productos.crear': 'PRODUCTO_CREAR',
  'productos.editar': 'PRODUCTO_EDITAR',
  'stock.ver': 'STOCK_VER',
  'stock.configurar': 'STOCK_CONFIGURAR',
  'movimientos.ver': 'MOVIMIENTO_VER',
  'movimientos.registrar': 'MOVIMIENTO_REGISTRAR',
  alertaver: 'ALERTA_VER',
  alertastock: 'ALERTA_VER',
  alerta_stock: 'ALERTA_VER',
  'alerta.stock': 'ALERTA_VER',
  alerta_ver: 'ALERTA_VER',
  'alerta.ver': 'ALERTA_VER',
  'alertas.ver': 'ALERTA_VER',
  cajaver: 'CAJA_VER',
  cajaeditar: 'CAJA_EDITAR',
  cajaabrir: 'CAJA_ABRIR',
  cajacerrar: 'CAJA_CERRAR',
  'caja.ver': 'CAJA_VER',
  'caja.editar': 'CAJA_EDITAR',
  'caja.abrir': 'CAJA_ABRIR',
  'caja.cerrar': 'CAJA_CERRAR',
  'cajas.ver': 'CAJA_VER',
  'cajas.editar': 'CAJA_EDITAR',
  'cajas.abrir': 'CAJA_ABRIR',
  'cajas.cerrar': 'CAJA_CERRAR',
  'ventas.ver': 'VENTA_VER',
  'ventas.crear': 'VENTA_CREAR',
  'ventas.anular': 'VENTA_ANULAR',
  'ventas.descuento': 'VENTA_DESCUENTO',
  'facturas.emitir': 'FACTURA_EMITIR',
  'facturas.reimprimir': 'FACTURA_REIMPRIMIR',
  'reportes.generar': 'REPORTE_GENERAR',
  'reportes.exportar': 'REPORTE_EXPORTAR',
  'dashboard.ver': 'DASHBOARD_VER',
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
  private readonly permissionsSignal = signal<CompanyPermissionsByCode>(
    this.readStoredPermissions(),
  );
  private readonly camposProductoSignal = signal<CamposProducto>(camposProductoPorDefecto());
  private readonly activeSubscriptionSignal = signal<CompanyActiveSubscription | null>(
    this.readStoredSubscription(),
  );
  private readonly planConfigurationSignal = signal<CompanyPlanConfiguration>(
    this.readStoredPlanConfiguration(),
  );

  readonly permissions = this.permissionsSignal.asReadonly();
  readonly camposProducto = this.camposProductoSignal.asReadonly();
  readonly activeSubscription = this.activeSubscriptionSignal.asReadonly();
  readonly planConfiguration = this.planConfigurationSignal.asReadonly();

  obtenerMisCamposProducto(idEmpresa: string | number): Observable<CamposProducto> {
    return this.apiService
      .get<Partial<CamposProducto>>(`/api/roles/empresa/${idEmpresa}/mis-campos`)
      .pipe(
        map((data) => this.normalizarCamposProducto(data)),
        tap((campos) => this.camposProductoSignal.set(campos)),
      );
  }

  obtenerCamposRol(idRol: string | number, formulario = 'producto'): Observable<CampoPrivilegio[]> {
    return this.apiService.get<CampoPrivilegio[]>(
      `/api/roles/${idRol}/campos?formulario=${encodeURIComponent(formulario)}`,
    );
  }

  guardarCamposRol(
    idRol: string | number,
    campos: CampoPrivilegio[],
  ): Observable<CampoPrivilegio[]> {
    return this.apiService.put<
      CampoPrivilegio[],
      { formulario: string; campos: CampoPrivilegio[] }
    >(`/api/roles/${idRol}/campos`, { formulario: 'producto', campos });
  }

  hasPermission(permission: CompanyPermissionCode): boolean {
    const permissions = this.permissionsSignal();
    return permissions[permission] === true;
  }

  savePermissions(permissions: CompanyPermission[]): CompanyPermissionsByCode {
    const permissionsByCode = this.createEmptyPermissions();

    if (permissions && permissions.length > 0) {
      for (const permission of permissions) {
        const permissionCode = this.normalizePermissionCode(permission.codigo);

        if (permissionCode) {
          permissionsByCode[permissionCode] = permission.activo_rol_permiso === true;
        }
      }
    }

    this.permissionsSignal.set(permissionsByCode);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(permissionsByCode));

    return permissionsByCode;
  }

  saveActiveSubscription(subscription: CompanyActiveSubscription | null): CompanyPlanConfiguration {
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
    const emptyPermissions = this.createEmptyPermissions();
    this.permissionsSignal.set(emptyPermissions);
    this.activeSubscriptionSignal.set(null);
    this.planConfigurationSignal.set({ ...EMPTY_PLAN_CONFIGURATION });
    this.camposProductoSignal.set(camposProductoPorDefecto());
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(SUBSCRIPTION_STORAGE_KEY);
    localStorage.removeItem(PLAN_CONFIGURATION_STORAGE_KEY);
  }

  private normalizarCamposProducto(
    data: Partial<CamposProducto> | null | undefined,
  ): CamposProducto {
    const campos = camposProductoPorDefecto();

    for (const campo of CAMPOS_PRODUCTO) {
      const estado = data?.[campo];
      if (estado === 'EDITABLE' || estado === 'SOLO_LECTURA' || estado === 'OCULTO') {
        campos[campo] = estado;
      }
    }

    return campos;
  }

  private createEmptyPermissions(): CompanyPermissionsByCode {
    return { ...EMPTY_COMPANY_PERMISSIONS };
  }

  private isCompanyPermissionCode(code: string): code is CompanyPermissionCode {
    return COMPANY_PERMISSION_CODES.includes(code as CompanyPermissionCode);
  }

  private normalizePermissionCode(code: string): CompanyPermissionCode | null {
    if (this.isCompanyPermissionCode(code)) {
      return code;
    }

    const normalizedCode = code.trim().toLowerCase();
    const compactCode = normalizedCode.replace(/[._-]/g, '');

    return PERMISSION_CODE_ALIASES[normalizedCode] ?? PERMISSION_CODE_ALIASES[compactCode] ?? null;
  }

  private readStoredPermissions(): CompanyPermissionsByCode {
    const emptyPermissions = this.createEmptyPermissions();
    const storedPermissions = localStorage.getItem(STORAGE_KEY);

    if (!storedPermissions) {
      return COMPANY_PERMISSION_CODES.reduce(
        (permissions, code) => {
          permissions[code] = true;
          return permissions;
        },
        { ...emptyPermissions },
      );
    }

    try {
      const parsedPermissions = JSON.parse(storedPermissions) as Partial<
        Record<CompanyPermissionCode, unknown>
      >;
      const activeCount = Object.values(parsedPermissions).filter(Boolean).length;
      if (activeCount === 0) {
        return COMPANY_PERMISSION_CODES.reduce(
          (permissions, code) => {
            permissions[code] = true;
            return permissions;
          },
          { ...emptyPermissions },
        );
      }

      return COMPANY_PERMISSION_CODES.reduce(
        (permissions, code) => {
          permissions[code] = Boolean(parsedPermissions[code]);
          return permissions;
        },
        { ...emptyPermissions },
      );
    } catch {
      return emptyPermissions;
    }
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
