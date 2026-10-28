import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable, forkJoin } from 'rxjs';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';
import { Icon } from '../../../../../shared/components/icon/icon';
import { CompanyPermissionsService } from '../../../../../core/services/company-permissions.service';
import {
  CompanyService,
  RoleListItem,
  RoleDetailResponse,
  PermissionModuleWithPermissions,
  UpdateRoleRequest,
} from '../../../../../core/services/company.service';
import {
  ACTIONS,
  CatalogPermission,
  ResourceMatrix,
  buildPermissionMatrix,
  dependencyWarnings,
  filteredMatrix,
} from './permission-matrix';

@Component({
  selector: 'app-rols',
  imports: [FormsModule, Navbar, Sidebar, Icon],
  templateUrl: './rols.html',
  styleUrl: './rols.css',
})
export class Rols implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);
  protected readonly permissions = inject(CompanyPermissionsService);
  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected roles: RoleListItem[] = [];
  protected matrix: ResourceMatrix[] = [];
  protected selected = new Set<number>();
  protected delegated = new Set<number>();
  private grantable = new Set<number>();
  protected searchRoles = '';
  protected status = 'all';
  protected searchPermissions = '';
  protected showDelegation = false;
  protected editor = false;
  protected editingId: number | null = null;
  protected readOnly = false;
  protected form = { nombre: '', descripcion: '', activo: true };
  protected loading = true;
  protected loadingDetail = false;
  protected saving = false;
  protected error = '';
  protected success = '';
  protected capabilities = { crear: false, editar: false };
  protected readonly actions = ACTIONS;
  private catalog: PermissionModuleWithPermissions[] = [];
  protected get visibleRoles() {
    const q = this.searchRoles.trim().toLocaleLowerCase('es');
    return this.roles.filter(
      (r) =>
        (this.status === 'all' || r.activo === (this.status === 'active')) &&
        (!q || (r.nombre + ' ' + r.descripcion).toLocaleLowerCase('es').includes(q)),
    );
  }
  protected get visibleMatrix() {
    return filteredMatrix(this.matrix, this.searchPermissions);
  }
  protected get warnings() {
    return dependencyWarnings(this.matrix, this.selected);
  }
  protected get mode() {
    return this.editingId === null ? 'create' : 'update';
  }
  protected has(code: string) {
    return this.permissions.hasPermission(code);
  }
  protected canField(field: string) {
    return (
      !this.readOnly && this.has('role:' + this.mode) && this.has('role:' + this.mode + ':' + field)
    );
  }
  protected get canCreate() {
    return (
      this.capabilities.crear &&
      this.has('role:create') &&
      this.has('role:create:nombre') &&
      this.has('role:create:permissions')
    );
  }
  protected get canSave() {
    return (
      !this.readOnly &&
      !this.loading &&
      !this.loadingDetail &&
      !this.saving &&
      !!this.form.nombre.trim() &&
      this.has('role:' + this.mode) &&
      (this.editingId !== null || this.canCreate)
    );
  }
  protected get canSeeGrants() {
    return this.editingId === null || this.has('role:read:permissions');
  }
  ngOnInit() {
    this.load();
  }
  protected load() {
    this.loading = true;
    this.error = '';
    forkJoin({
      roles: this.api.getRoles(this.companyId),
      catalog: this.api.getPermisosPorModulo(),
      grantable: this.api.getDelegables(this.companyId),
      capabilities: this.api.getRoleCapabilities(this.companyId),
    }).subscribe({
      next: (result) => {
        this.roles = result.roles;
        this.catalog = result.catalog;
        this.matrix = buildPermissionMatrix(this.catalog);
        this.grantable = new Set(result.grantable);
        this.capabilities = result.capabilities;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.loading = false;
        this.error = e?.error?.detail ?? 'No se pudieron cargar los roles y permisos.';
        this.cdr.detectChanges();
      },
    });
  }
  protected create() {
    if (!this.canCreate) return;
    this.editingId = null;
    this.readOnly = false;
    this.form = { nombre: '', descripcion: '', activo: true };
    this.selected = new Set();
    this.delegated = new Set();
    this.searchPermissions = '';
    this.editor = true;
    this.error = '';
    this.success = '';
  }
  protected open(role: RoleListItem) {
    if (role.protegido || role.tipo === 'SISTEMA') return;
    this.readOnly = !role.administrable;
    this.editor = true;
    this.editingId = role.id_rol;
    this.loadingDetail = true;
    this.error = '';
    this.success = '';
    this.searchPermissions = '';
    this.selected = new Set();
    this.delegated = new Set();
    this.api.getRoleById(role.id_rol).subscribe({
      next: (detail) => {
        if (this.editingId !== detail.id_rol) return;
        this.form = {
          nombre: detail.nombre,
          descripcion: detail.descripcion,
          activo: detail.activo,
        };
        this.selected = new Set(
          (detail.rol_permisos ?? []).filter((g) => g.activo).map((g) => g.id_permiso),
        );
        this.delegated = new Set(
          (detail.rol_permisos ?? [])
            .filter((g) => g.activo && g.puede_delegar)
            .map((g) => g.id_permiso),
        );
        this.loadingDetail = false;
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.loadingDetail = false;
        this.readOnly = true;
        this.error = e?.error?.detail ?? 'No se pudo cargar el rol.';
        this.cdr.detectChanges();
      },
    });
  }
  protected close() {
    if (this.saving) return;
    this.editor = false;
    this.editingId = null;
    this.error = '';
  }
  protected canGrant(p: CatalogPermission) {
    return (
      this.canField('permissions') &&
      this.grantable.has(p.id_permiso) &&
      this.permissions.canDelegate(p.id_permiso) &&
      this.has(p.codigo_canonico)
    );
  }
  protected toggle(p: CatalogPermission, checked: boolean) {
    if (!this.canGrant(p)) return;
    const next = new Set(this.selected);
    if (checked) next.add(p.id_permiso);
    else {
      next.delete(p.id_permiso);
      this.delegated.delete(p.id_permiso);
    }
    this.selected = next;
  }
  protected toggleDelegation(p: CatalogPermission, checked: boolean) {
    if (!this.canGrant(p) || !this.selected.has(p.id_permiso)) return;
    const next = new Set(this.delegated);
    checked ? next.add(p.id_permiso) : next.delete(p.id_permiso);
    this.delegated = next;
  }
  protected actionLabel(action: string) {
    return this.actions[action] ?? action;
  }
  protected save(event: Event) {
    event.preventDefault();
    if (!this.canSave) return;
    this.saving = true;
    this.error = '';
    this.success = '';
    const data: UpdateRoleRequest = {
      ...(this.canField('nombre') ? { nombre: this.form.nombre.trim() } : {}),
      ...(this.canField('descripcion') ? { descripcion: this.form.descripcion.trim() } : {}),
      ...(this.canField('activo') ? { activo: this.form.activo } : {}),
      ...(this.canField('permissions') && this.canSeeGrants
        ? {
            permiso_ids: [...this.selected],
            delegable_ids: [...this.delegated].filter((id) => this.selected.has(id)),
          }
        : {}),
    };
    const request: Observable<unknown> =
      this.editingId === null
        ? this.api.crearRol(this.companyId, {
            ...data,
            nombre: this.form.nombre.trim(),
            permiso_ids: [...this.selected],
          })
        : this.api.actualizarRol(this.editingId, data);
    request.subscribe({
      next: () => {
        this.saving = false;
        this.editor = false;
        this.editingId = null;
        this.success = 'Rol guardado correctamente.';
        this.refreshContext();
        this.load();
        this.cdr.detectChanges();
      },
      error: (e) => {
        this.saving = false;
        this.error =
          e?.error?.detail ?? 'No se pudo guardar el rol. Tus cambios siguen en el formulario.';
        this.cdr.detectChanges();
      },
    });
  }
  private refreshContext() {
    this.permissions.clearPermissions();
    this.api.getMisPermisos(this.companyId).subscribe({
      next: (r) => {
        this.permissions.savePermissions(r.permisos);
        this.permissions.saveActiveSubscription(r.suscripcion_activa);
        this.permissions.watchContext(this.companyId);
        this.cdr.detectChanges();
      },
      error: () => this.permissions.clearPermissions(),
    });
  }
}
