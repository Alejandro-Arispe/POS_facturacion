import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Plan, PagoService } from '../../../core/services/pago.service';
import { Navbar } from '../../../shared/components/navbar/navbar';

@Component({
  selector: 'app-plans',
  imports: [Navbar, RouterLink],
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
      error: () => {
        this.error = 'No se pudieron cargar los planes. Intenta nuevamente.';
        this.cargando = false;
        this.cdr.detectChanges();
      },
    });
  }
}
