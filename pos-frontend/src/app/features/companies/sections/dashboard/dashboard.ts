import { Component, OnInit, OnDestroy, inject, ChangeDetectorRef } from '@angular/core';
import { PreferencesService } from '../../../../core/services/preferences.service';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';

import { Navbar } from '../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../shared/components/sidebar/sidebar';
import { CompanyService } from '../../../../core/services/company.service';
import { CompanyWebSocketService } from '../../../../core/services/company-websocket.service';

type DashboardState = 'loading' | 'ready' | 'empty' | 'error';

interface KpiCard {
  title: string;
  value: string;
  icon: string;
}

interface RankedClient {
  posicion: number;
  id_cliente: number;
  nombre: string;
  total_comprado: number;
  cantidad_compras: number;
  ticket_promedio: number;
  ultima_compra: string | null;
  categoria: 'Oro' | 'Plata' | 'Bronce';
  puntaje: number;
}

interface Recommendation {
  tipo: string;
  titulo: string;
  mensaje: string;
  prioridad: 'alta' | 'media' | 'baja';
  metrica?: string;
  valor?: number | string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MoneyPipe, CommonModule, Navbar, Sidebar],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, OnDestroy {
  private readonly preferences = inject(PreferencesService);
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  private readonly wsService = inject(CompanyWebSocketService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected companyName = 'Comercial Nova';

  protected uiState: DashboardState = 'loading';

  private wsSubscription: Subscription | null = null;
  protected receivedDashboardData: any = null;

  ngOnInit(): void {
    this.wsSubscription = this.wsService.messages$.subscribe({
      next: (msg) => {
        this.handleWsMessage(msg);
      }
    });

    if (this.companyId) {
      this.companyService.obtenerEmpresa(this.companyId).subscribe({
        next: (company) => {
          if (company && company.nombre) {
            this.companyName = company.nombre;
          }
        },
        error: (err) => {
          console.error('Error fetching company details:', err);
        }
      });
    }
  }

  ngOnDestroy(): void {
    if (this.wsSubscription) {
      this.wsSubscription.unsubscribe();
    }
  }

  private handleWsMessage(msg: any): void {
    if (msg && msg.tipo === 'dashboard') {
      const data = Array.isArray(msg.datos) ? msg.datos[0] : msg.datos;
      if (data) {
        this.receivedDashboardData = data;
        this.uiState = 'ready';
      } else {
        this.receivedDashboardData = null;
        this.uiState = 'empty';
      }

      this.cdr.detectChanges();
    }
  }

  protected get shouldShowEmptyState(): boolean {
    return this.uiState === 'empty';
  }

  protected get shouldShowErrorState(): boolean {
    return this.uiState === 'error';
  }

  protected get shouldShowLoadingState(): boolean {
    return this.uiState === 'loading';
  }

  protected get shouldShowDashboard(): boolean {
    return this.uiState === 'ready';
  }

  protected get kpiCards(): KpiCard[] {
    const ind = this.receivedDashboardData?.indicadores || {};
    const salesToday = Number(ind.ventas_hoy ?? 0);
    const averageTicket = Number(ind.ticket_promedio ?? 0);
    const openRegisters = Number(ind.cajas_abiertas ?? 0);
    const topProd = ind.producto_estrella
      ? `${ind.producto_estrella.nombre || ''} (${ind.producto_estrella.unidades ?? 0} uds)`
      : '-';
    const lowStock = Number(ind.productos_bajo_stock ?? 0);
    const outOfStock = Number(ind.productos_agotados ?? 0);
    const income = Number(ind.ingresos_dia ?? 0);
    const expenses = Number(ind.egresos_dia ?? 0);
    const netFlow = Number(ind.flujo_neto ?? 0);

    return [
      { title: 'Ventas de hoy', value: this.formatCurrency(salesToday), icon: 'VEN' },
      { title: 'Ticket promedio', value: this.formatCurrency(averageTicket), icon: 'AVG' },
      { title: 'Cajas abiertas', value: this.formatInteger(openRegisters), icon: 'CAJ' },
      { title: 'Producto estrella', value: topProd, icon: 'TOP' },
      { title: 'Productos con bajo stock', value: this.formatInteger(lowStock), icon: 'LOW' },
      { title: 'Productos agotados', value: this.formatInteger(outOfStock), icon: 'OUT' },
      { title: 'Ingresos del dia', value: this.formatCurrency(income), icon: 'ING' },
      { title: 'Egresos del dia', value: this.formatCurrency(expenses), icon: 'EGR' },
      { title: 'Flujo neto', value: this.formatCurrency(netFlow), icon: 'NET' },
    ];
  }

  private formatCurrency(value: number): string {
    return this.preferences.formatMoney(value);
  }

  private formatInteger(value: number): string {
    return new Intl.NumberFormat('es-BO', {
      maximumFractionDigits: 0,
    }).format(value);
  }

  protected get rankingClientes(): RankedClient[] {
    return Array.isArray(this.receivedDashboardData?.ranking_clientes)
      ? this.receivedDashboardData.ranking_clientes
      : [];
  }

  protected get recomendaciones(): Recommendation[] {
    return Array.isArray(this.receivedDashboardData?.recomendaciones_ia)
      ? this.receivedDashboardData.recomendaciones_ia
      : [];
  }
}
