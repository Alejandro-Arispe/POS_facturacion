import { ChangeDetectorRef, Component } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { OnboardingService } from '../../../core/services/onboarding.service';
import { OnboardingSteps } from '../../../shared/components/onboarding-steps/onboarding-steps';

@Component({
  selector: 'app-login',
  imports: [TranslatePipe, FormsModule, RouterLink, OnboardingSteps],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  protected readonly idPlan: number | null;
  protected email = '';
  protected contrasena = '';
  protected cargandoLogin = false;
  protected errorLogin = '';
  protected mostrarModalRecuperacion = false;
  protected emailRecuperacion = '';
  protected cargandoRecuperacion = false;
  protected errorRecuperacion = '';
  protected mensajeRecuperacion = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
    private readonly onboarding: OnboardingService,
  ) {
    this.idPlan =
      onboarding.selectPlan(route.snapshot.queryParamMap.get('plan')) ?? onboarding.plan;
  }

  protected iniciarSesion(event: SubmitEvent): void {
    event.preventDefault();
    this.errorLogin = '';

    const email = this.email.trim();

    if (!email || !this.contrasena) {
      this.errorLogin = 'Ingresa tu correo y contrasena.';
      return;
    }

    this.cargandoLogin = true;

    this.authService.login({ email, contrasena: this.contrasena }).subscribe({
      next: (response) => {
        this.cargandoLogin = false;
        this.authService.saveSession(response.access_token);

        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

        if (returnUrl) {
          void this.router.navigateByUrl(returnUrl);
          return;
        }

        const plan = this.idPlan;

        if (plan) {
          this.onboarding.clearPendingEmail();
          void this.router.navigate(['/onboarding/checkout'], { queryParams: { plan } });
          return;
        }

        void this.router.navigate(['/my-companies']);
      },
      error: () => {
        this.cargandoLogin = false;
        this.errorLogin = 'Correo o contrasena incorrectos.';
        this.cdr.detectChanges();
      },
    });
  }

  protected abrirModalRecuperacion(event: MouseEvent): void {
    event.preventDefault();
    this.mostrarModalRecuperacion = true;
    this.emailRecuperacion = '';
    this.errorRecuperacion = '';
    this.mensajeRecuperacion = '';
  }

  protected cerrarModalRecuperacion(): void {
    if (this.cargandoRecuperacion) {
      return;
    }

    this.mostrarModalRecuperacion = false;
    this.errorRecuperacion = '';
    this.mensajeRecuperacion = '';
  }

  protected recuperarContrasena(event: SubmitEvent): void {
    event.preventDefault();
    this.errorRecuperacion = '';
    this.mensajeRecuperacion = '';

    const email = this.emailRecuperacion.trim();

    if (!email) {
      this.errorRecuperacion = 'Ingresa tu correo electronico.';
      return;
    }

    this.cargandoRecuperacion = true;

    this.authService.forgotPassword({ email }).subscribe({
      next: (response) => {
        this.cargandoRecuperacion = false;
        this.mensajeRecuperacion =
          response.mensaje ?? response.message ?? 'Solicitud enviada correctamente.';
        this.cdr.detectChanges();

        setTimeout(() => {
          this.mostrarModalRecuperacion = false;
          this.emailRecuperacion = '';
          this.mensajeRecuperacion = '';
          this.cdr.detectChanges();
        }, 3000);
      },
      error: () => {
        this.cargandoRecuperacion = false;
        this.errorRecuperacion = 'No se pudo enviar la recuperacion. Intenta nuevamente.';
        this.cdr.detectChanges();
      },
    });
  }
}
