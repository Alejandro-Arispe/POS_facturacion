import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  Branch,
  CompanyService,
  UpdateBranchRequest,
} from '../../../../../../core/services/company.service';
import { Navbar } from '../../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../../shared/components/sidebar/sidebar';

@Component({
  selector: 'app-edit-branch',
  imports: [FormsModule, Navbar, Sidebar, RouterLink],
  templateUrl: './edit-branch.html',
  styleUrl: './edit-branch.css',
})
export class EditBranch implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly companyService = inject(CompanyService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly branchId = this.route.snapshot.paramMap.get('branchId') ?? '';
  protected cargandoSucursal = false;
  protected cargandoEdicion = false;
  protected errorEdicion = '';
  protected mensajeEdicion = '';
  protected readonly form: UpdateBranchRequest = {
    nombre: '',
    direccion: '',
    telefono: '',
    ciudad: '',
    activo: true,
  };

  ngOnInit(): void {
    this.cargarSucursal();
  }

  protected guardarCambios(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    if (!this.branchId) {
      this.errorEdicion = 'No se encontro la sucursal a editar.';
      return;
    }

    const payload: UpdateBranchRequest = {
      nombre: this.form.nombre.trim(),
      direccion: this.form.direccion.trim(),
      telefono: this.form.telefono.trim(),
      ciudad: this.form.ciudad.trim(),
      activo: this.form.activo,
    };

    if (!payload.nombre || !payload.direccion || !payload.telefono || !payload.ciudad) {
      this.errorEdicion = 'Completa todos los campos de la sucursal.';
      return;
    }

    this.cargandoEdicion = true;

    this.companyService.actualizarSucursal(this.branchId, payload).subscribe({
      next: () => {
        this.cargandoEdicion = false;
        this.mensajeEdicion = 'Sucursal actualizada correctamente.';
        this.cdr.detectChanges();

        void this.router.navigate(['/company', this.companyId, 'branches']);
      },
      error: () => {
        this.cargandoEdicion = false;
        this.errorEdicion = 'No se pudo actualizar la sucursal. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarSucursal(): void {
    this.errorEdicion = '';

    if (!this.companyId || !this.branchId) {
      this.errorEdicion = 'No se encontro la sucursal a editar.';
      return;
    }

    this.cargandoSucursal = true;

    this.companyService.obtenerSucursal(this.companyId, this.branchId).subscribe({
      next: (branch) => {
        this.cargandoSucursal = false;
        this.llenarFormulario(branch);
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoSucursal = false;
        this.errorEdicion = 'No se pudieron cargar los datos de la sucursal.';
        this.cdr.detectChanges();
      },
    });
  }

  private llenarFormulario(branch: Branch): void {
    this.form.nombre = branch.nombre ?? '';
    this.form.direccion = branch.direccion ?? '';
    this.form.telefono = branch.telefono ?? '';
    this.form.ciudad = branch.ciudad ?? '';
    this.form.activo = branch.activo ?? true;
  }
}

