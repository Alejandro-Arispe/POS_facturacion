import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { CompanyService, CreateCompanyRequest } from '../../../../core/services/company.service';
import { PagoService } from '../../../../core/services/pago.service';
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

  protected readonly idPlan: number | null;
  protected cargandoRegistro = false;
  protected errorRegistro = '';
  protected mensajeRegistro = '';

  constructor(
    private readonly companyService: CompanyService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
    private readonly pagoService: PagoService,
    route: ActivatedRoute,
  ) {
    const plan = Number(route.snapshot.queryParamMap.get('plan'));
    this.idPlan = Number.isInteger(plan) && plan > 0 ? plan : null;
  }

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
      next: (response) => {
        const idEmpresa = this.obtenerIdEmpresa(response);

        if (this.idPlan !== null && idEmpresa !== null) {
          this.iniciarPago(idEmpresa, this.idPlan);
          return;
        }

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

  private iniciarPago(idEmpresa: number, idPlan: number): void {
    this.mensajeRegistro = 'Empresa registrada. Redirigiendo al pago del plan...';
    this.cdr.detectChanges();

    this.pagoService.crearCheckout({ id_empresa: idEmpresa, id_plan: idPlan }).subscribe({
      next: (checkout) => {
        this.pagoService.guardarSesionStripe(checkout.session_id);
        window.location.href = checkout.checkout_url;
      },
      error: () => {
        this.cargandoRegistro = false;
        this.mensajeRegistro = '';
        this.errorRegistro =
          'La empresa se registro, pero no se pudo iniciar el pago. Puedes pagar el plan desde Mis empresas.';
        this.cdr.detectChanges();
      },
    });
  }

  private obtenerIdEmpresa(response: unknown): number | null {
    const raw = response as { empresa?: Record<string, unknown> } & Record<string, unknown>;
    const empresa = raw?.empresa ?? raw;
    const id = Number(empresa?.['idEmpresa'] ?? empresa?.['id_empresa'] ?? empresa?.['id']);

    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
