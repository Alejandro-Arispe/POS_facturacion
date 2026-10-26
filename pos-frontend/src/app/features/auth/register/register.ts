import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService, RegisterRequest } from '../../../core/services/auth.service';
import { OnboardingService } from '../../../core/services/onboarding.service';
import { PagoService, Plan } from '../../../core/services/pago.service';
import { OnboardingSteps } from '../../../shared/components/onboarding-steps/onboarding-steps';
import { Icon } from '../../../shared/components/icon/icon';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, OnboardingSteps, Icon],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  protected readonly form = {
    nombreCompleto: '',
    email: '',
    fechaNacimiento: '',
    documento: '',
    genero: '',
    telefono: '',
    contrasena: '',
    confirmarContrasena: '',
  };
  protected readonly idPlan: number | null;
  protected plan: Plan | null = null;
  protected etapa: 'datos' | 'verificar' = 'datos';
  protected codigo = '';
  protected emailParaVerificar = '';
  protected verificado = false;
  protected cargando = false;
  protected mensaje = '';
  protected error = '';

  constructor(
    private readonly auth: AuthService,
    private readonly cdr: ChangeDetectorRef,
    private readonly router: Router,
    private readonly onboarding: OnboardingService,
    pago: PagoService,
    route: ActivatedRoute,
  ) {
    this.idPlan =
      onboarding.selectPlan(route.snapshot.queryParamMap.get('plan')) ?? onboarding.plan;
    if (!this.idPlan) {
      void router.navigate(['/planes']);
      return;
    }
    if (onboarding.pendingEmail) {
      this.emailParaVerificar = onboarding.pendingEmail;
      this.etapa = 'verificar';
    }
    pago.getPlanes().subscribe({
      next: (planes) => {
        this.plan = planes.find((p) => p.id_plan === this.idPlan) ?? null;
        if (!this.plan) void router.navigate(['/planes']);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = this.detail(
          err,
          'No se pudo comprobar el plan. Vuelve a elegirlo para continuar.',
        );
        this.cdr.markForCheck();
      },
    });
  }

  protected registrar(event: SubmitEvent): void {
    event.preventDefault();
    if (this.cargando || !this.plan) return;
    this.error = '';
    this.mensaje = '';
    if (this.form.contrasena !== this.form.confirmarContrasena) {
      this.error = 'Las contraseñas no coinciden.';
      return;
    }
    const payload: RegisterRequest = {
      email: this.form.email.trim(),
      contrasena: this.form.contrasena,
      nombre_completo: this.form.nombreCompleto.trim(),
      fecha_nacimiento: this.form.fechaNacimiento,
      genero: this.form.genero,
      telefono: this.form.telefono.trim(),
      documento: this.form.documento.trim(),
    };
    this.cargando = true;
    this.auth.register(payload).subscribe({
      next: (response) => {
        this.cargando = false;
        this.emailParaVerificar = response.email ?? payload.email;
        this.onboarding.pendingEmail = this.emailParaVerificar;
        this.etapa = 'verificar';
        this.verificado = response.activo === true;
        this.mensaje = this.verificado
          ? 'Tu cuenta está lista. Continuando con tu plan…'
          : 'Tu cuenta está pendiente de verificación. Revisa el código enviado a tu correo.';
        if (!response.email_enviado && !this.verificado)
          this.error =
            'La cuenta se creó, pero no se pudo enviar el correo. Puedes reintentar el envío.';
        this.cdr.markForCheck();
        if (this.verificado) this.continuar();
      },
      error: (err) => {
        this.cargando = false;
        this.error = this.detail(err, 'No se pudo crear la cuenta. Intenta nuevamente.');
        this.cdr.markForCheck();
      },
    });
  }

  protected verificar(event: SubmitEvent): void {
    event.preventDefault();
    if (this.cargando) return;
    this.cargando = true;
    this.error = '';
    this.mensaje = '';
    this.auth.verifyCode({ email: this.emailParaVerificar, codigo: this.codigo.trim() }).subscribe({
      next: () => {
        this.cargando = false;
        this.verificado = true;
        this.onboarding.clearPendingEmail();
        this.mensaje = 'Correo verificado. Continuando con tu plan…';
        this.cdr.markForCheck();
        this.continuar();
      },
      error: (err) => {
        this.cargando = false;
        this.error = this.detail(err, 'No se pudo verificar el código.');
        this.cdr.markForCheck();
      },
    });
  }

  protected reenviar(): void {
    if (this.cargando) return;
    this.cargando = true;
    this.error = '';
    this.mensaje = '';
    this.auth.resendCode({ email: this.emailParaVerificar }).subscribe({
      next: (response) => {
        this.cargando = false;
        this.mensaje = response.mensaje ?? 'Revisa tu correo para encontrar el nuevo código.';
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.cargando = false;
        this.error = this.detail(err, 'No se pudo reenviar el código.');
        this.cdr.markForCheck();
      },
    });
  }

  protected continuar(): void {
    this.onboarding.clearPendingEmail();
    if (!this.form.contrasena) {
      void this.router.navigate(['/login'], { queryParams: { plan: this.idPlan } });
      return;
    }
    this.cargando = true;
    this.error = '';
    this.auth
      .login({ email: this.emailParaVerificar, contrasena: this.form.contrasena })
      .subscribe({
        next: (response) => {
          this.auth.saveSession(response.access_token);
          this.form.contrasena = '';
          this.form.confirmarContrasena = '';
          void this.router.navigate(['/onboarding/checkout'], {
            queryParams: { plan: this.idPlan },
          });
        },
        error: () => {
          this.cargando = false;
          void this.router.navigate(['/login'], { queryParams: { plan: this.idPlan } });
        },
      });
  }

  private detail(err: { error?: { detail?: unknown } }, fallback: string): string {
    return typeof err.error?.detail === 'string' ? err.error.detail : fallback;
  }
}
