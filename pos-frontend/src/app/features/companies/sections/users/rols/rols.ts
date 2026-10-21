import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../core/services/company-permissions.service';
import {
  CompanyService,
  CreateRoleResponse,
  RoleDetailResponse,
  RoleListItem,
  PermissionModuleWithPermissions,
  UpdateRoleRequest,
} from '../../../../../core/services/company.service';

type RolesTab = 'create' | 'list' | 'edit';

interface PermissionMatcher {
  codes?: readonly string[];
  labels?: readonly string[];
}

interface PermissionDependencyRule {
  source: PermissionMatcher;
  required: PermissionMatcher;
}

const PERMISSION_DEPENDENCY_RULES: PermissionDependencyRule[] = [
  { source: { codes: ['USUARIO_CREAR', 'USUARIO_EDITAR'] }, required: { codes: ['USUARIO_VER'] } },
  { source: { codes: ['ROL_CREAR', 'ROL_EDITAR'] }, required: { codes: ['ROL_VER'] } },
  { source: { codes: ['SUCURSAL_CREAR', 'SUCURSAL_EDITAR'] }, required: { codes: ['SUCURSAL_VER'] } },
  { source: { codes: ['PRODUCTO_CREAR', 'PRODUCTO_EDITAR'] }, required: { codes: ['PRODUCTO_VER'] } },
  { source: { codes: ['STOCK_CONFIGURAR'] }, required: { codes: ['STOCK_VER'] } },
  { source: { codes: ['MOVIMIENTO_REGISTRAR'] }, required: { codes: ['MOVIMIENTO_VER'] } },
  { source: { codes: ['VENTA_CREAR', 'VENTA_ANULAR', 'VENTA_DESCUENTO', 'FACTURA_EMITIR', 'FACTURA_REIMPRIMIR'] }, required: { codes: ['VENTA_VER'] } },
  { source: { codes: ['CLIENTE_CREAR', 'CLIENTE_EDITAR'] }, required: { codes: ['CLIENTE_VER'] } },
  { source: { codes: ['CATEGORIA_CREAR', 'CATEGORIA_EDITAR'] }, required: { codes: ['CATEGORIA_VER'] } },
  { source: { codes: ['CAJA_EDITAR', 'CAJA_ABRIR', 'CAJA_CERRAR'] }, required: { codes: ['CAJA_VER'] } },
  {
    source: { labels: ['registrar movimiento de caja'] },
    required: { labels: ['ver movimiento de caja', 'ver movimientos de caja'] },
  },
];

const HIDDEN_ROLE_PERMISSION_CODES = new Set([
  'VENTA_ANULAR',
  'VENTA_DESCUENTO',
  'FACTURA_EMITIR',
  'FACTURA_REIMPRIMIR',
]);

interface PermissionAction {
  id: number;
  key: string;
  code: string;
  label: string;
  enabled: boolean;
}

interface PermissionModule {
  key: string;
  label: string;
  enabled: boolean;
  actions: PermissionAction[];
}

interface RoleRow {
  id_rol: number;
  nombre: string;
  tipo: string;
  descripcion: string;
  activo: boolean;
  permisos?: string[];
}

@Component({
  selector: 'app-rols',
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './rols.html',
  styleUrl: './rols.css',
})
export class Rols implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected activeTab: RolesTab = 'create';
  protected roleName = '';
  protected roleDescription = '';
  protected editRoleName = '';
  protected editRoleDescription = '';
  protected editRoleActive = true;
  protected editingRoleIndex: number | null = null;
  protected roles: RoleRow[] = [];
  protected permissionModules: PermissionModule[] = [];
  protected editPermissionModules: PermissionModule[] = [];
  protected cargandoPermisos = false;
  protected cargandoRoles = false;
  protected creandoRol = false;
  protected guardandoEdicion = false;
  protected errorPermisos = '';
  protected errorRoles = '';
  private availablePermissionModules: PermissionModuleWithPermissions[] = [];
  private editingRoleDetail: RoleDetailResponse | null = null;

  ngOnInit(): void {
    if (!this.hasPermission('ROL_CREAR')) {
      this.activeTab = 'list';
    }

    this.cargarRoles();
    this.cargarPermisosPorModulo();
  }

  protected setActiveTab(tab: RolesTab): void {
    if (tab === 'create' && !this.hasPermission('ROL_CREAR')) {
      return;
    }

    if (tab === 'edit' && (this.editingRoleIndex === null || !this.hasPermission('ROL_EDITAR'))) {
      return;
    }

    this.activeTab = tab;
  }

  protected onModuleToggle(module: PermissionModule): void {
    if (module.enabled) {
      return;
    }

    for (const action of module.actions) {
      action.enabled = false;
    }
  }

  protected onActionToggle(
    modules: PermissionModule[],
    module: PermissionModule,
    action: PermissionAction,
    enabled: boolean,
  ): void {
    action.enabled = enabled;

    if (!enabled) {
      return;
    }

    this.syncPermissionDependencies(modules);
  }

  protected crearRol(event: SubmitEvent): void {
    event.preventDefault();

    if (!this.hasPermission('ROL_CREAR')) {
      this.errorRoles = 'No tienes permiso para crear roles.';
      return;
    }

    const nombre = this.roleName.trim();
    const permisoIds = this.obtenerPermisoIdsSeleccionados();
    const permisos = this.obtenerPermisosSeleccionados();

    if (!nombre || permisoIds.length === 0 || this.creandoRol) {
      return;
    }

    this.creandoRol = true;
    this.errorRoles = '';

    this.companyService
      .crearRol(this.companyId, {
        nombre,
        permiso_ids: permisoIds,
      })
      .subscribe({
        next: (response) => {
          this.roles = [this.mapCreatedRoleFromApi(response, permisos), ...this.roles];
          this.roleName = '';
          this.roleDescription = '';
          this.limpiarPermisos(this.permissionModules);
          this.creandoRol = false;
          this.activeTab = 'list';
          this.cdr.detectChanges();
        },
        error: () => {
          this.creandoRol = false;
          this.errorRoles = 'No se pudo crear el rol. Intenta nuevamente.';
          this.cdr.detectChanges();
        },
      });
  }

  protected editarRol(index: number): void {
    if (!this.hasPermission('ROL_EDITAR')) {
      return;
    }

    const role = this.roles[index];

    this.editingRoleIndex = index;
    this.editingRoleDetail = null;
    this.editRoleName = role.nombre;
    this.editRoleDescription = role.descripcion;
    this.editRoleActive = role.activo;
    this.editPermissionModules = this.createPermissionModules();
    this.activeTab = 'edit';

    this.companyService.getRoleById(role.id_rol).subscribe({
      next: (detail) => {
        if (this.editingRoleIndex !== index) {
          return;
        }

        this.editingRoleDetail = detail;
        this.editRoleName = detail.nombre;
        this.editRoleDescription = detail.descripcion;
        this.editRoleActive = detail.activo;
        this.editPermissionModules = this.createPermissionModulesForRole(detail.rol_permisos);
        this.syncPermissionDependencies(this.editPermissionModules);
        this.cdr.detectChanges();
      },
      error: () => {
        if (this.editingRoleIndex !== index) {
          return;
        }

        this.errorRoles = 'No se pudo cargar el detalle del rol. Intenta nuevamente.';
        this.editPermissionModules = this.createPermissionModules();
        this.cdr.detectChanges();
      },
    });
  }

  protected cancelarEdicion(): void {
    this.editingRoleIndex = null;
    this.editingRoleDetail = null;
    this.editRoleName = '';
    this.editRoleDescription = '';
    this.editRoleActive = true;
    this.editPermissionModules = this.createPermissionModules();
    this.activeTab = 'list';
  }

  protected guardarEdicion(event: SubmitEvent): void {
    event.preventDefault();

    if (!this.hasPermission('ROL_EDITAR')) {
      this.errorRoles = 'No tienes permiso para editar roles.';
      return;
    }

    if (this.editingRoleIndex === null) {
      return;
    }

    const role = this.roles[this.editingRoleIndex];

    if (!role || this.guardandoEdicion) {
      return;
    }

    const payload: UpdateRoleRequest = {
      nombre: this.editRoleName.trim(),
      permiso_ids: this.obtenerPermisoIdsSeleccionados(this.editPermissionModules),
      activo: this.editRoleActive,
    };

    this.guardandoEdicion = true;
    this.errorRoles = '';

    this.companyService.actualizarRol(role.id_rol, payload).subscribe({
      next: (updatedRole) => {
        const activePermissionIds = updatedRole.rol_permisos
          .filter((rolPermiso) => rolPermiso.activo)
          .map((rolPermiso) => rolPermiso.id_permiso);

        this.roles = this.roles.map((currentRole) => {
          if (currentRole.id_rol !== updatedRole.id_rol) {
            return currentRole;
          }

          return {
            ...this.mapRoleFromApi(updatedRole),
            permisos: this.getPermissionLabelsByIds(activePermissionIds),
          };
        });

        this.guardandoEdicion = false;
        this.cancelarEdicion();
        this.cdr.detectChanges();
      },
      error: () => {
        this.guardandoEdicion = false;
        this.errorRoles = 'No se pudo actualizar el rol. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private obtenerPermisosSeleccionados(modules = this.permissionModules): string[] {
    return modules.flatMap((module) => {
      if (!module.enabled) {
        return [];
      }

      return module.actions
        .filter((action) => action.enabled)
        .map((action) => `${module.label}: ${action.label}`);
    });
  }

  private obtenerPermisoIdsSeleccionados(modules = this.permissionModules): number[] {
    return modules.flatMap((module) => {
      if (!module.enabled) {
        return [];
      }

      return module.actions.filter((action) => action.enabled).map((action) => action.id);
    });
  }

  private limpiarPermisos(modules: PermissionModule[]): void {
    for (const module of modules) {
      module.enabled = false;

      for (const action of module.actions) {
        action.enabled = false;
      }
    }
  }

  private createPermissionModules(selectedPermissions: string[] = []): PermissionModule[] {
    const selected = new Set(selectedPermissions);

    return this.availablePermissionModules.map((module) => {
      const actions = module.permisos.filter((permission) => !this.isHiddenRolePermission(permission.codigo)).map((permission) => {
        const permissionLabel = this.formatPermissionLabel(module.nombre, permission.nombre);

        return {
          id: permission.id_permiso,
          key: permission.codigo,
          code: permission.codigo,
          label: permission.nombre,
          enabled: selected.has(permissionLabel),
        };
      });

      return {
        key: module.codigo,
        label: module.nombre,
        enabled: actions.some((action) => action.enabled),
        actions,
      };
    }).filter((module) => module.actions.length > 0);
  }

  private createPermissionModulesForRole(rolPermisos: RoleDetailResponse['rol_permisos'] = []): PermissionModule[] {
    const selectedPermissionIds = new Set(
      rolPermisos.filter((rolPermiso) => rolPermiso.activo).map((rolPermiso) => rolPermiso.id_permiso),
    );

    return this.availablePermissionModules.map((module) => {
      const actions = module.permisos.filter((permission) => !this.isHiddenRolePermission(permission.codigo)).map((permission) => ({
        id: permission.id_permiso,
        key: permission.codigo,
        code: permission.codigo,
        label: permission.nombre,
        enabled: selectedPermissionIds.has(permission.id_permiso),
      }));

      return {
        key: module.codigo,
        label: module.nombre,
        enabled: actions.some((action) => action.enabled),
        actions,
      };
    }).filter((module) => module.actions.length > 0);
  }

  private syncPermissionDependencies(modules: PermissionModule[]): void {
    const selectedActions = modules.flatMap((module) =>
      module.actions.filter((action) => action.enabled).map((action) => ({ module, action })),
    );

    for (const { module, action } of selectedActions) {
      this.applyPermissionDependencies(modules, module, action, new Set());
    }
  }

  private applyPermissionDependencies(
    modules: PermissionModule[],
    module: PermissionModule,
    action: PermissionAction,
    visited: Set<string>,
  ): void {
    const visitKey = `${module.key}:${action.key}`;

    if (visited.has(visitKey)) {
      return;
    }

    visited.add(visitKey);

    for (const dependency of this.getMatchingRequiredActions(modules, module, action)) {
      dependency.module.enabled = true;

      if (!dependency.action.enabled) {
        dependency.action.enabled = true;
      }

      this.applyPermissionDependencies(modules, dependency.module, dependency.action, visited);
    }
  }

  private getMatchingRequiredActions(
    modules: PermissionModule[],
    module: PermissionModule,
    action: PermissionAction,
  ): Array<{ module: PermissionModule; action: PermissionAction }> {
    const rule = PERMISSION_DEPENDENCY_RULES.find((candidate) => this.matchesPermission(candidate.source, module, action));

    if (!rule) {
      return [];
    }

    return this.findActionsByMatcher(modules, rule.required);
  }

  private findActionsByMatcher(
    modules: PermissionModule[],
    matcher: PermissionMatcher,
  ): Array<{ module: PermissionModule; action: PermissionAction }> {
    return modules.flatMap((candidateModule) =>
      candidateModule.actions
        .filter((candidateAction) => this.matchesPermission(matcher, candidateModule, candidateAction))
        .map((candidateAction) => ({ module: candidateModule, action: candidateAction })),
    );
  }

  private matchesPermission(matcher: PermissionMatcher, module: PermissionModule, action: PermissionAction): boolean {
    const normalizedModule = this.normalizeText(module.label);
    const normalizedAction = this.normalizeText(action.label);
    const normalizedCode = action.code.toUpperCase();

    const matchesCodes = matcher.codes?.some((code) => code === normalizedCode) ?? false;
    const matchesLabels = matcher.labels?.some((label) => {
      const normalizedLabel = this.normalizeText(label);
      return normalizedAction.includes(normalizedLabel) || normalizedModule.includes(normalizedLabel);
    }) ?? false;

    return matchesCodes || matchesLabels;
  }

  private normalizeText(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  }

  private isHiddenRolePermission(code: string): boolean {
    return HIDDEN_ROLE_PERMISSION_CODES.has(code.trim().toUpperCase());
  }

  private cargarPermisosPorModulo(): void {
    this.cargandoPermisos = true;
    this.errorPermisos = '';

    this.companyService.getPermisosPorModulo().subscribe({
      next: (modules) => {
        this.availablePermissionModules = modules;
        this.permissionModules = this.createPermissionModules();

        if (this.editingRoleIndex !== null && this.editingRoleDetail !== null) {
          this.editPermissionModules = this.createPermissionModulesForRole(this.editingRoleDetail.rol_permisos);
          this.syncPermissionDependencies(this.editPermissionModules);
        } else {
          this.editPermissionModules = this.createPermissionModules();
        }

        this.cargandoPermisos = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.availablePermissionModules = [];
        this.permissionModules = [];
        this.editPermissionModules = [];
        this.cargandoPermisos = false;
        this.errorPermisos = 'No se pudieron cargar los permisos. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarRoles(): void {
    this.cargandoRoles = true;
    this.errorRoles = '';

    this.companyService.getRoles(this.companyId).subscribe({
      next: (roles) => {
        this.roles = roles.map((role) => this.mapRoleFromApi(role));
        this.cargandoRoles = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.roles = [];
        this.cargandoRoles = false;
        this.errorRoles = 'No se pudieron cargar los roles. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private mapRoleFromApi(role: RoleListItem): RoleRow {
    return {
      id_rol: role.id_rol,
      nombre: role.nombre,
      tipo: role.tipo,
      descripcion: role.descripcion,
      activo: role.activo,
      permisos: [],
    };
  }

  private mapCreatedRoleFromApi(response: CreateRoleResponse, selectedPermissions: string[]): RoleRow {
    return {
      ...this.mapRoleFromApi(response.rol),
      permisos: selectedPermissions,
    };
  }

  private getPermissionLabelsByIds(permissionIds: number[]): string[] {
    const selectedIds = new Set(permissionIds);

    return this.availablePermissionModules.flatMap((module) =>
      module.permisos
        .filter((permission) => selectedIds.has(permission.id_permiso))
        .map((permission) => this.formatPermissionLabel(module.nombre, permission.nombre)),
    );
  }

  private formatPermissionLabel(moduleLabel: string, permissionLabel: string): string {
    return `${moduleLabel}: ${permissionLabel}`;
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    return this.companyPermissionsService.permissions()[permission] === true;
  }
}

