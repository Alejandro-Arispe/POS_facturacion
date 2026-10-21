import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { CompanyService, CreateCompanyRequest } from '../../../../core/services/company.service';
import { Navbar } from '../../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-register-company',
  imports: [FormsModule, Navbar, RouterLink],
  templateUrl: './register-company.html',
  styleUrl: './register-company.css',
})
export class RegisterCompany {
  protected readonly form: CreateCompanyRequest = {
    nombre: '',
    razon_social: '',
    nit: '',
    correo: '',
  };

  protected cargandoRegistro = false;
  protected errorRegistro = '';
  protected mensajeRegistro = '';

  constructor(
    private readonly companyService: CompanyService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  protected registrarEmpresa(event: SubmitEvent): void {
    event.preventDefault();
    this.errorRegistro = '';
    this.mensajeRegistro = '';

    const payload: CreateCompanyRequest = {
      nombre: this.form.nombre.trim(),
      razon_social: this.form.razon_social.trim(),
      nit: this.form.nit.trim(),
      correo: this.form.correo.trim(),
    };

    if (!payload.nombre || !payload.razon_social || !payload.nit || !payload.correo) {
      this.errorRegistro = 'Completa todos los campos de la empresa.';
      return;
    }

    this.cargandoRegistro = true;

    this.companyService.crearEmpresa(payload).subscribe({
      next: () => {
        this.cargandoRegistro = false;
        this.mensajeRegistro = 'Empresa registrada correctamente.';
        this.cdr.detectChanges();

        void this.router.navigate(['/my-companies']);
      },
      error: (error: { error?: { detail?: string } }) => {
        this.cargandoRegistro = false;
        this.errorRegistro = error?.error?.detail ?? 'No se pudo registrar la empresa. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }
}

