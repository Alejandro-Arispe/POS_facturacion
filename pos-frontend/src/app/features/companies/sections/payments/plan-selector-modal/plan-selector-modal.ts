import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

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

  constructor(private readonly pagoService: PagoService) {}

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
      },
      error: (err: any) => {
        this.errorPlanes = 'No se pudieron cargar los planes. Intente de nuevo mas tarde.';
        this.cargandoPlanes = false;
        console.error('Error al cargar planes', err);
      },
    });
  }

  seleccionarPlan(plan: Plan): void {
    if (this.procesandoPagoId !== null) {
      return;
    }

    this.procesandoPagoId = plan.id_plan;

    this.pagoService.crearCheckout({
      id_empresa: this.idEmpresa,
      id_plan: plan.id_plan,
    }).subscribe({
      next: (response: CheckoutResponse) => {
        this.pagoService.guardarSesionStripe(response.session_id);
        window.location.href = response.checkout_url;
      },
      error: (err: any) => {
        this.errorPlanes = 'No se pudo iniciar el proceso de pago.';
        this.procesandoPagoId = null;
        console.error('Error al crear checkout', err);
      },
    });
  }

  cerrarModal(): void {
    this.cerrar.emit();
  }
}
