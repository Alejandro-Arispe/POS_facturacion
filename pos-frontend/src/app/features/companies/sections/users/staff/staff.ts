import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  Branch,
  CompanyStaffMember,
  CompanyService,
  RoleListItem,
} from '../../../../../core/services/company.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../core/services/company-permissions.service';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

type StaffTab = 'invite' | 'list' | 'edit';

@Component({
  selector: 'app-staff',
  imports: [FormsModule, Navbar, Sidebar],
  templateUrl: './staff.html',
  styleUrl: './staff.css',
})
export class Staff implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected selectedBranchId = '';
  protected activeTab: StaffTab = 'list';
  protected emailInvitacion = '';
  protected selectedRole = '1';
  protected selectedInvitationBranchIds: string[] = [];
  protected editingStaffId: number | null = null;
  protected editEmail = '';
  protected editRole = '1';
  protected editBranchIds: string[] = [];
  protected editStaffActive = true;
  protected cargandoSucursales = false;
  protected cargandoInvitacion = false;
  protected cargandoPersonal = false;
  protected cargandoEdicion = false;
  protected errorSucursales = '';
  protected errorInvitacion = '';
  protected errorPersonal = '';
  protected errorEdicion = '';
  protected errorRoles = '';
  protected mensajeInvitacion = '';
  protected mensajeEdicion = '';
  protected branches: Branch[] = [];
  protected staffMembers: CompanyStaffMember[] = [];
  protected roles: Array<{ value: string; label: string }> = [];

  ngOnInit(): void {
    this.cargarRoles();
    this.cargarSucursales();
  }

  protected setActiveTab(tab: StaffTab): void {
    if (tab === 'invite' && !this.hasPermission('USUARIO_CREAR')) {
      return;
    }

    if (tab === 'edit' && (this.editingStaffId === null || !this.hasPermission('USUARIO_EDITAR'))) {
      return;
    }

    this.activeTab = tab;

    if (tab === 'list') {
      this.cargarPersonal();
    }
  }

  protected enviarInvitacion(event: SubmitEvent): void {
    event.preventDefault();
    this.errorInvitacion = '';
    this.mensajeInvitacion = '';

    if (!this.hasPermission('USUARIO_CREAR')) {
      this.errorInvitacion = 'No tienes permiso para invitar personal.';
      return;
    }

    const email = this.emailInvitacion.trim();
    const idSucursales = this.selectedInvitationBranchIds
      .map((branchId) => Number(branchId))
      .filter((branchId) => Number.isFinite(branchId));
    const idRol = Number(this.selectedRole);

    if (!this.companyId || idSucursales.length === 0) {
      this.errorInvitacion = 'Selecciona al menos una sucursal para enviar la invitacion.';
      return;
    }

    if (!Number.isFinite(idRol)) {
      this.errorInvitacion = 'Selecciona un rol para enviar la invitacion.';
      return;
    }

    if (!email) {
      this.errorInvitacion = 'Ingresa el correo del empleado.';
      return;
    }

    this.cargandoInvitacion = true;

    this.companyService
      .invitarEmpleado(this.companyId, {
        email,
        id_sucursales: idSucursales,
        id_rol: idRol,
      })
      .subscribe({
        next: (response) => {
          this.cargandoInvitacion = false;
          this.mensajeInvitacion =
            response?.mensaje ??
            response?.message ??
            `Invitacion enviada correctamente a ${idSucursales.length} sucursal(es).`;
          this.emailInvitacion = '';
          this.cargarPersonal();
          this.cdr.detectChanges();
        },
        error: (error: { error?: { detail?: string } }) => {
          this.cargandoInvitacion = false;
          this.errorInvitacion = error?.error?.detail ?? 'No se pudo enviar la invitacion. Intenta nuevamente.';
          this.cdr.detectChanges();
        },
      });
  }

  protected onBranchChange(): void {
    this.errorInvitacion = '';
    this.mensajeInvitacion = '';
    this.cargarPersonal();
  }

  protected isInvitationBranchSelected(branchId: string): boolean {
    return this.selectedInvitationBranchIds.includes(branchId);
  }

  protected toggleInvitationBranch(branchId: string, checked: boolean): void {
    if (checked) {
      this.selectedInvitationBranchIds = Array.from(new Set([...this.selectedInvitationBranchIds, branchId]));
      return;
    }

    this.selectedInvitationBranchIds = this.selectedInvitationBranchIds.filter((id) => id !== branchId);
  }

  protected editarPersonal(member: CompanyStaffMember): void {
    if (!this.hasPermission('USUARIO_EDITAR')) {
      return;
    }

    const firstRelation = member.relaciones[0];

    this.errorEdicion = '';
    this.mensajeEdicion = '';
    this.editingStaffId = member.id_usuario;
    this.editEmail = member.usuario.email;
    this.editRole = this.roles.find((role) => role.value === String(firstRelation?.id_rol))?.value ?? this.selectedRole;
    this.editBranchIds = member.relaciones
      .filter((relation) => relation.activo)
      .map((relation) => String(relation.id_sucursal));
    this.editStaffActive = Boolean(member.usuario.activo && member.relaciones.some((relation) => relation.activo));
    this.activeTab = 'edit';
  }

  protected cancelarEdicion(): void {
    this.editingStaffId = null;
    this.editEmail = '';
    this.editRole = '1';
    this.editBranchIds = [];
    this.editStaffActive = true;
    this.cargandoEdicion = false;
    this.errorEdicion = '';
    this.mensajeEdicion = '';
    this.activeTab = 'list';
  }

  protected guardarEdicion(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.hasPermission('USUARIO_EDITAR')) {
      this.errorEdicion = 'No tienes permiso para editar personal.';
      return;
    }

    const email = this.editEmail.trim();
    const idSucursales = this.editBranchIds
      .map((branchId) => Number(branchId))
      .filter((branchId) => Number.isFinite(branchId));
    const idRol = Number(this.editRole);

    if (this.editingStaffId === null) {
      this.errorEdicion = 'Selecciona el personal que deseas editar.';
      return;
    }

    if (!email) {
      this.errorEdicion = 'No se encontro el correo del personal seleccionado.';
      return;
    }

    if (!Number.isFinite(idRol)) {
      this.errorEdicion = 'Selecciona un rol para editar el personal.';
      return;
    }

    if (!this.companyId || idSucursales.length === 0) {
      this.errorEdicion = 'Selecciona al menos una sucursal para editar el personal.';
      return;
    }

    this.cargandoEdicion = true;

    this.companyService
      .editarPersonalEmpresa(this.companyId, {
        email,
        id_sucursales: idSucursales,
        id_rol: idRol,
        activo: this.editStaffActive,
      })
      .subscribe({
        next: () => {
          this.cargandoEdicion = false;
          this.mensajeEdicion = 'Personal actualizado correctamente.';
          this.editingStaffId = null;
          this.editEmail = '';
          this.editRole = '1';
          this.editBranchIds = [];
          this.editStaffActive = true;
          this.activeTab = 'list';
          this.cargarPersonal();
          this.cdr.detectChanges();
        },
        error: (error: { error?: { detail?: string } }) => {
          this.cargandoEdicion = false;
          this.errorEdicion = error?.error?.detail ?? 'No se pudo editar el personal. Intenta nuevamente.';
          this.cdr.detectChanges();
        },
      });
  }

  protected isEditBranchSelected(branchId: string): boolean {
    return this.editBranchIds.includes(branchId);
  }

  protected toggleEditBranch(branchId: string, checked: boolean): void {
    if (checked) {
      this.editBranchIds = Array.from(new Set([...this.editBranchIds, branchId]));
      return;
    }

    this.editBranchIds = this.editBranchIds.filter((id) => id !== branchId);
  }

  protected obtenerIdSucursal(branch: Partial<Branch>): string {
    return String(branch.idSucursal ?? branch.id_sucursal ?? branch.id ?? '');
  }

  protected obtenerNombreSucursal(idSucursal: string): string {
    return this.branches.find((branch) => this.obtenerIdSucursal(branch) === idSucursal)?.nombre ?? idSucursal;
  }

  protected obtenerSucursalesPersonal(member: CompanyStaffMember): string {
    if (member.relaciones.length === 0) {
      return 'Sin sucursales asignadas';
    }

    return member.relaciones
      .map((relation) => this.obtenerNombreSucursal(String(relation.id_sucursal)))
      .join(', ');
  }

  protected obtenerRolPersonal(member: CompanyStaffMember): string {
    const idRol = member.relaciones[0]?.id_rol;

    return this.roles.find((role) => role.value === String(idRol))?.label ?? String(idRol ?? 'Sin rol');
  }

  protected estaPersonalActivo(member: CompanyStaffMember): boolean {
    return member.usuario.activo && member.relaciones.some((relation) => relation.activo);
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    return this.companyPermissionsService.permissions()[permission] === true;
  }

  private cargarSucursales(): void {
    this.errorSucursales = '';

    if (!this.companyId) {
      this.errorSucursales = 'No se encontro la empresa para cargar sus sucursales.';
      return;
    }

    this.cargandoSucursales = true;

    this.companyService.getSucursales(this.companyId).subscribe({
      next: (branches) => {
        this.branches = branches;
        this.selectedBranchId = this.obtenerIdSucursal(branches[0] ?? {});
        this.selectedInvitationBranchIds = this.selectedBranchId ? [this.selectedBranchId] : [];
        this.cargandoSucursales = false;
        this.cargarPersonal();
        this.cdr.detectChanges();
      },
      error: () => {
        this.branches = [];
        this.staffMembers = [];
        this.cargandoSucursales = false;
        this.errorSucursales = 'No se pudieron cargar las sucursales de la empresa.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarRoles(): void {
    this.errorRoles = '';

    if (!this.companyId) {
      this.roles = [];
      this.errorRoles = 'No se encontro la empresa para cargar sus roles.';
      return;
    }

    this.companyService.getRoles(this.companyId).subscribe({
      next: (roles) => {
        this.roles = roles
          .filter((role) => this.shouldShowRole(role))
          .map((role) => ({
            value: String(role.id_rol),
            label: role.nombre,
          }));

        if (!this.roles.some((role) => role.value === this.selectedRole)) {
          this.selectedRole = this.roles[0]?.value ?? '';
        }

        if (!this.roles.some((role) => role.value === this.editRole)) {
          this.editRole = this.selectedRole;
        }

        this.cdr.detectChanges();
      },
      error: () => {
        this.roles = [];
        this.errorRoles = 'No se pudieron cargar los roles de la empresa.';
        this.cdr.detectChanges();
      },
    });
  }

  private shouldShowRole(role: RoleListItem): boolean {
    const roleName = role.nombre.trim().toUpperCase();

    return roleName !== 'CLIENTE' && roleName !== 'ADMINISTRADOR';
  }

  private cargarPersonal(): void {
    this.errorPersonal = '';

    if (!this.companyId) {
      this.staffMembers = [];
      this.errorPersonal = 'No se encontro la empresa para cargar el personal.';
      return;
    }

    this.cargandoPersonal = true;

    this.companyService.getPersonalEmpresa(this.companyId).subscribe({
      next: (staffMembers) => {
        this.staffMembers = staffMembers;
        this.cargandoPersonal = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.staffMembers = [];
        this.cargandoPersonal = false;
        this.errorPersonal = 'No se pudo cargar el personal de la empresa.';
        this.cdr.detectChanges();
      },
    });
  }
}

