import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PagoService } from '../../../core/services/pago.service';
import { OnboardingService } from '../../../core/services/onboarding.service';
import { OnboardingSteps } from '../../../shared/components/onboarding-steps/onboarding-steps';
import { Navbar } from '../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-checkout',
  imports: [RouterLink, Navbar, OnboardingSteps],
  template: `<div class="page-container">
    <app-navbar />
    <main class="mx-auto grid max-w-2xl gap-8 px-4 py-8">
      <app-onboarding-steps [step]="3" />
      <section
        class="grid justify-items-center gap-5 rounded-2xl border border-line bg-surface p-8 text-center"
        aria-live="polite"
      >
        <h1 class="text-2xl font-bold">
          {{ error ? 'No se pudo abrir el pago' : 'Continuando con tu plan…' }}
        </h1>
        <p class="text-sm leading-6 text-muted">
          {{
            error ||
              'Te llevamos directamente a Stripe. Si elegiste el gratuito, se activará sin tarjeta.'
          }}
        </p>
        @if (error) {
          <button class="btn btn-primary" type="button" (click)="iniciar()">Reintentar</button>
        }
        <a routerLink="/planes" class="text-sm text-brand">Volver a planes</a>
      </section>
    </main>
  </div>`,
})
export class Checkout implements OnInit {
  protected error = '';
  private loading = false;
  private readonly plan: number | null;
  constructor(
    private readonly pago: PagoService,
    onboarding: OnboardingService,
    route: ActivatedRoute,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {
    this.plan = onboarding.selectPlan(route.snapshot.queryParamMap.get('plan')) ?? onboarding.plan;
  }
  ngOnInit(): void {
    this.iniciar();
  }
  protected iniciar(): void {
    if (this.loading) return;
    if (!this.plan) {
      void this.router.navigate(['/planes']);
      return;
    }
    this.loading = true;
    this.error = '';
    this.pago.crearCheckout({ id_plan: this.plan }).subscribe({
      next: (checkout) => {
        this.pago.guardarSesionStripe(checkout.session_id);
        window.location.assign(checkout.checkout_url);
      },
      error: (err) => {
        this.loading = false;
        this.error =
          typeof err.error?.detail === 'string'
            ? err.error.detail
            : 'Intenta nuevamente. Tu cuenta ya está guardada.';
        this.cdr.markForCheck();
      },
    });
  }
}
