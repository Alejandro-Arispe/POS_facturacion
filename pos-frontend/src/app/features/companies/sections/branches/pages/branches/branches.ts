import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { RouterLink } from '@angular/router';
import {
  Branch,
  CompanyService,
  CreateBranchRequest,
} from '../../../../../../core/services/company.service';
import {
  CompanyPermissionCode,
  CompanyPermissionsService,
} from '../../../../../../core/services/company-permissions.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-branches',
  imports: [FormsModule, Navbar, Sidebar, RouterLink],
  templateUrl: './branches.html',
  styleUrl: './branches.css',
})
export class Branches implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly companyPermissionsService = inject(CompanyPermissionsService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected activeTab: 'register' | 'list' = 'list';
  protected cargandoRegistro = false;
  protected cargandoSucursales = false;
  protected errorRegistro = '';
  protected errorSucursales = '';
  protected mensajeRegistro = '';
  protected branches: Branch[] = [];
  protected readonly branchForm: CreateBranchRequest = {
    nombre: '',
    direccion: '',
    telefono: '',
    ciudad: '',
  };

  ngOnInit(): void {
    this.cargarSucursales();
  }

  protected setActiveTab(tab: 'register' | 'list'): void {
    if (tab === 'register' && !this.hasPermission('SUCURSAL_CREAR')) {
      return;
    }

    this.activeTab = tab;

    if (tab === 'list') {
      this.cargarSucursales();
    }
  }

  protected obtenerIdSucursal(branch: Branch): string | number {
    return branch.idSucursal ?? branch.id_sucursal ?? branch.id ?? branch.nombre;
  }

  protected hasPermission(permission: CompanyPermissionCode): boolean {
    return this.companyPermissionsService.hasPermission(permission);
  }

  protected registrarSucursal(event: SubmitEvent): void {
    event.preventDefault();
    this.errorRegistro = '';
    this.mensajeRegistro = '';

    if (!this.hasPermission('SUCURSAL_CREAR')) {
      this.errorRegistro = 'No tienes permiso para registrar sucursales.';
      return;
    }

    if (!this.companyId) {
      this.errorRegistro = 'No se encontro la empresa para registrar la sucursal.';
      return;
    }

    const payload: CreateBranchRequest = {
      nombre: this.branchForm.nombre.trim(),
      direccion: this.branchForm.direccion.trim(),
      telefono: this.branchForm.telefono.trim(),
      ciudad: this.branchForm.ciudad.trim(),
    };

    if (!payload.nombre || !payload.direccion || !payload.telefono || !payload.ciudad) {
      this.errorRegistro = 'Completa todos los campos de la sucursal.';
      return;
    }

    this.cargandoRegistro = true;

    this.companyService.crearSucursal(this.companyId, payload).subscribe({
      next: () => {
        this.cargandoRegistro = false;
        this.mensajeRegistro = 'Sucursal registrada correctamente.';
        this.limpiarFormularioSucursal();
        this.cargarSucursales();
        this.cdr.detectChanges();
      },
      error: (error: { error?: { detail?: string } }) => {
        this.cargandoRegistro = false;
        this.errorRegistro = error?.error?.detail ?? 'No se pudo registrar la sucursal. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
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
        this.cargandoSucursales = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.branches = [];
        this.cargandoSucursales = false;
        this.errorSucursales = 'No se pudieron cargar las sucursales. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private limpiarFormularioSucursal(): void {
    this.branchForm.nombre = '';
    this.branchForm.direccion = '';
    this.branchForm.telefono = '';
    this.branchForm.ciudad = '';
  }
}

