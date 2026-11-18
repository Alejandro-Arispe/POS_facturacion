import { ChangeDetectorRef, Component } from '@angular/core';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth.service';
import { OnboardingService } from '../../../core/services/onboarding.service';
import { CompanyService } from '../../../core/services/company.service';
import { CompanyPermissionsService } from '../../../core/services/company-permissions.service';
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
    private readonly companies: CompanyService,
    private readonly permissions: CompanyPermissionsService,
  ) {
    this.idPlan =
      onboarding.selectPlan(route.snapshot.queryParamMap.get('plan')) ?? onboarding.plan;
  }

  /**
   * HU-02: superadmin → panel; con una sola empresa con plan activo:
   * admin (dashboard:read) → dashboard, cajero (sale:create) → PDV (cajas de su sucursal);
   * en cualquier otro caso → Mis empresas.
   */
  private redirigirPorRol(): void {
    const fallback = () => void this.router.navigate(['/my-companies']);
    if (this.authService.isSuperAdmin()) {
      void this.router.navigate(['/superadmin']);
      return;
    }
    this.companies.getMisEmpresas().subscribe({
      next: (list) => {
        const activas = list.filter((c) =>
          ['activo', 'activa'].includes((c.suscripcion_activa?.estado ?? '').toLowerCase()),
        );
        if (activas.length !== 1) return fallback();
        const c = activas[0];
        const id = String(c.idEmpresa ?? c.id_empresa ?? c.empresa_id ?? c.id ?? '');
        this.companies.getMisPermisos(id).subscribe({
          next: (access) => {
            this.permissions.savePermissions(access.permisos);
            this.permissions.saveActiveSubscription(access.suscripcion_activa);
            if (this.permissions.hasPermission('dashboard:read')) {
              void this.router.navigate(['/company', id, 'dashboard']);
            } else if (this.permissions.hasPermission('sale:create')) {
              this.companies.getSucursales(id).subscribe({
                next: (branches) => {
                  const b = branches.length === 1 ? branches[0] : null;
                  const branchId = b ? (b.id_sucursal ?? b.idSucursal ?? b.id) : null;
                  void this.router.navigate(
                    branchId != null
                      ? ['/company', id, 'branch', branchId, 'cash-register']
                      : ['/company', id, 'branches'],
                  );
                },
                error: fallback,
              });
            } else {
              fallback();
            }
          },
          error: fallback,
        });
      },
      error: fallback,
    });
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

        this.redirigirPorRol();
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
