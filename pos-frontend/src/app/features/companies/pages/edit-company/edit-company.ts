import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import {
  Company,
  CompanyService,
  UpdateCompanyRequest,
} from '../../../../core/services/company.service';
import { Navbar } from '../../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-edit-company',
  imports: [FormsModule, Navbar, RouterLink],
  templateUrl: './edit-company.html',
  styleUrl: './edit-company.css',
})
export class EditCompany implements OnInit {
  protected readonly form: UpdateCompanyRequest = {
    nombre: '',
    razon_social: '',
    nit: '',
    correo: '',
    activo: true,
  };

  protected cargandoEdicion = false;
  protected cargandoEmpresa = false;
  protected errorEdicion = '';
  protected mensajeEdicion = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly companyService: CompanyService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cargarEmpresa();
  }

  protected guardarCambios(event: SubmitEvent): void {
    event.preventDefault();
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    const idEmpresa = this.route.snapshot.paramMap.get('idEmpresa');

    if (!idEmpresa) {
      this.errorEdicion = 'No se encontro la empresa a editar.';
      return;
    }

    const payload: UpdateCompanyRequest = {
      nombre: this.form.nombre.trim(),
      razon_social: this.form.razon_social.trim(),
      nit: this.form.nit.trim(),
      correo: this.form.correo.trim(),
      activo: this.form.activo,
    };

    if (!payload.nombre || !payload.razon_social || !payload.nit || !payload.correo) {
      this.errorEdicion = 'Completa todos los campos de la empresa.';
      return;
    }

    this.cargandoEdicion = true;

    this.companyService.actualizarEmpresa(idEmpresa, payload).subscribe({
      next: () => {
        this.cargandoEdicion = false;
        this.mensajeEdicion = 'Empresa actualizada correctamente.';
        this.cdr.detectChanges();

        void this.router.navigate(['/my-companies']);
      },
      error: () => {
        this.cargandoEdicion = false;
        this.errorEdicion = 'No se pudo actualizar la empresa. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }

  private cargarEmpresa(): void {
    this.errorEdicion = '';
    this.mensajeEdicion = '';

    const idEmpresa = this.route.snapshot.paramMap.get('idEmpresa');

    if (!idEmpresa) {
      this.errorEdicion = 'No se encontro la empresa a editar.';
      return;
    }

    this.cargandoEmpresa = true;

    this.companyService.obtenerEmpresa(idEmpresa).subscribe({
      next: (company) => {
        this.cargandoEmpresa = false;
        this.llenarFormulario(company);
        this.cdr.detectChanges();
      },
      error: () => {
        this.cargandoEmpresa = false;
        this.errorEdicion = 'No se pudieron cargar los datos de la empresa.';
        this.cdr.detectChanges();
      },
    });
  }

  private llenarFormulario(company: Company): void {
    this.form.nombre = company.nombre ?? '';
    this.form.razon_social = company.razon_social ?? '';
    this.form.nit = company.nit ?? '';
    this.form.correo = company.correo ?? '';
    this.form.activo = company.activo ?? true;
  }
}

