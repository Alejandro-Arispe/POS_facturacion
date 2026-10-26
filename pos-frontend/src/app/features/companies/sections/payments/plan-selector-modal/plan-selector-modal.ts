import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { OnboardingService } from '../../../../../core/services/onboarding.service';

import { CheckoutResponse, PagoService, Plan } from '../../../../../core/services/pago.service';

@Component({
  selector: 'app-plan-selector-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './plan-selector-modal.html',
  styleUrl: './plan-selector-modal.css',
})
export class PlanSelectorModalComponent implements OnInit {
  @Input() idEmpresa!: number;
  @Output() cerrar = new EventEmitter<void>();

  planes: Plan[] = [];
  cargandoPlanes = true;
  errorPlanes: string | null = null;
  procesandoPagoId: number | null = null;

  constructor(
    private readonly pagoService: PagoService,
    private readonly onboarding: OnboardingService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cargarPlanes();
  }

  cargarPlanes(): void {
    this.cargandoPlanes = true;
    this.errorPlanes = null;

    this.pagoService.getPlanes().subscribe({
      next: (planes: Plan[]) => {
        this.planes = planes;
        this.cargandoPlanes = false;
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        this.errorPlanes =
          typeof err.error?.detail === 'string'
            ? err.error.detail
            : 'No se pudieron cargar los planes. Intente de nuevo mas tarde.';
        this.cargandoPlanes = false;
        console.error('Error al cargar planes', err);
        this.cdr.markForCheck();
      },
    });
  }

  seleccionarPlan(plan: Plan): void {
    if (this.procesandoPagoId !== null) {
      return;
    }

    this.procesandoPagoId = plan.id_plan;
    this.onboarding.selectPlan(plan.id_plan);
    this.onboarding.saveCompany(this.idEmpresa, '', plan.id_plan);

    this.pagoService
      .crearCheckout({
        id_empresa: this.idEmpresa,
        id_plan: plan.id_plan,
      })
      .subscribe({
        next: (response: CheckoutResponse) => {
          this.pagoService.guardarSesionStripe(response.session_id);
          window.location.href = response.checkout_url;
        },
        error: (err: any) => {
          this.errorPlanes =
            typeof err.error?.detail === 'string'
              ? err.error.detail
              : 'No se pudo iniciar el proceso de pago.';
          this.procesandoPagoId = null;
          console.error('Error al crear checkout', err);
          this.cdr.markForCheck();
        },
      });
  }

  cerrarModal(): void {
    this.cerrar.emit();
  }
}
