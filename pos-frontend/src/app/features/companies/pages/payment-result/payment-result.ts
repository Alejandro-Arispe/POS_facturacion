import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  PagoService,
  SuscripcionActiva,
  CompraPlanDisponible,
} from '../../../../core/services/pago.service';
import { CompanyService } from '../../../../core/services/company.service';
import { CompanyPermissionsService } from '../../../../core/services/company-permissions.service';
import { OnboardingService } from '../../../../core/services/onboarding.service';
import { OnboardingSteps } from '../../../../shared/components/onboarding-steps/onboarding-steps';
import { Icon } from '../../../../shared/components/icon/icon';
import { Navbar } from '../../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-payment-result',
  imports: [CommonModule, RouterLink, OnboardingSteps, Icon, Navbar],
  templateUrl: './payment-result.html',
  styleUrl: './payment-result.css',
})
export class PaymentResultComponent implements OnInit, OnDestroy {
  protected estado: 'cargando' | 'exito' | 'error' | 'pendiente' | 'cancelado' = 'cargando';
  protected mensajeError = '';
  protected suscripcion: SuscripcionActiva | null = null;
  protected compra: CompraPlanDisponible | null = null;
  protected idEmpresa: number | null = null;
  protected idPlan: number | null = null;
  protected entrando = false;
  private retry?: ReturnType<typeof setTimeout>;
  private destroyed = false;
  private sessionId: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly pago: PagoService,
    private readonly onboarding: OnboardingService,
    private readonly companies: CompanyService,
    private readonly permissions: CompanyPermissionsService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const pending = this.onboarding.company;
    this.idEmpresa =
      this.positive(this.route.snapshot.queryParamMap.get('empresa')) ?? pending?.id ?? null;
    this.idPlan =
      this.positive(this.route.snapshot.queryParamMap.get('plan')) ?? pending?.planId ?? null;
    if (this.router.url.includes('/cancel')) {
      this.estado = 'cancelado';
      return;
    }
    this.sessionId =
      this.route.snapshot.queryParamMap.get('session_id') ?? this.pago.obtenerSesionStripe();
    if (!this.sessionId) {
      this.estado = 'error';
      this.mensajeError = 'No encontramos la sesión del pago. Puedes continuar desde Mis empresas.';
      return;
    }
    this.confirmarPago();
  }

  protected confirmarPago(intentos = 0): void {
    if (!this.sessionId || this.destroyed) return;
    if (this.retry) clearTimeout(this.retry);
    this.estado = 'cargando';
    this.mensajeError = '';
    this.pago.confirmarPago({ session_id: this.sessionId }).subscribe({
      next: (response) => {
        if (this.destroyed) return;
        const subscription = response.suscripcion;
        const purchase = response.compra;
        if (purchase?.estado === 'pagado') {
          this.compra = purchase;
          this.idEmpresa = purchase.id_empresa;
          this.estado = 'exito';
          this.pago.limpiarSesionStripe();
          this.onboarding.complete();
        } else if (
          subscription &&
          ['activo', 'activa'].includes(subscription.estado.toLowerCase())
        ) {
          this.suscripcion = subscription;
          this.idEmpresa = subscription.id_empresa;
          this.estado = 'exito';
          this.pago.limpiarSesionStripe();
          this.onboarding.complete();
        } else if (intentos < 5) {
          this.retry = setTimeout(() => this.confirmarPago(intentos + 1), 2000);
        } else {
          this.estado = 'pendiente';
          this.mensajeError =
            'Stripe todavía no confirmó el pago. Puedes comprobarlo otra vez sin volver a pagar.';
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (this.destroyed) return;
        this.estado = 'error';
        this.mensajeError =
          typeof err.error?.detail === 'string'
            ? err.error.detail
            : 'No se pudo confirmar el pago. Intenta comprobarlo otra vez.';
        this.cdr.markForCheck();
      },
    });
  }

  protected entrar(): void {
    if (!this.idEmpresa || this.entrando) return;
    this.entrando = true;
    this.mensajeError = '';
    this.companies.getMisPermisos(this.idEmpresa).subscribe({
      next: (response) => {
        this.permissions.savePermissions(response.permisos);
        this.permissions.saveActiveSubscription(response.suscripcion_activa);
        void this.router.navigate(['/company', this.idEmpresa, 'branches']);
      },
      error: () => {
        this.entrando = false;
        this.mensajeError =
          'No se pudo abrir la empresa. Intenta nuevamente o entra desde Mis empresas.';
        this.cdr.markForCheck();
      },
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    if (this.retry) clearTimeout(this.retry);
  }
  private positive(raw: unknown): number | null {
    const id = Number(raw);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
}
