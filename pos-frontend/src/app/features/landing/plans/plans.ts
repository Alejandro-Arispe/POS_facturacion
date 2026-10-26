import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Plan, PagoService } from '../../../core/services/pago.service';
import { Navbar } from '../../../shared/components/navbar/navbar';
import { OnboardingSteps } from '../../../shared/components/onboarding-steps/onboarding-steps';
import { Icon } from '../../../shared/components/icon/icon';
import { DecimalPipe } from '@angular/common';
import { OnboardingService } from '../../../core/services/onboarding.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-plans',
  imports: [Navbar, RouterLink, OnboardingSteps, Icon, DecimalPipe],
  templateUrl: './plans.html',
  styleUrl: './plans.css',
})
export class Plans implements OnInit {
  protected planes: Plan[] = [];
  protected cargando = true;
  protected error = '';

  constructor(
    private readonly pagoService: PagoService,
    private readonly cdr: ChangeDetectorRef,
    protected readonly onboarding: OnboardingService,
    protected readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.cargarPlanes();
  }

  protected cargarPlanes(): void {
    this.cargando = true;
    this.error = '';

    this.pagoService.getPlanes().subscribe({
      next: (planes) => {
        this.planes = planes;
        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error =
          typeof err.error?.detail === 'string'
            ? err.error.detail
            : 'No se pudieron cargar los planes. Intenta nuevamente.';
        this.cargando = false;
        this.cdr.detectChanges();
      },
    });
  }
}
