import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { PreferencesService } from '../../../../core/services/preferences.service';
import { CompanyService } from '../../../../core/services/company.service';
import { CompanyPermissionsService } from '../../../../core/services/company-permissions.service';
import { CompanyWebSocketService } from '../../../../core/services/company-websocket.service';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';
import { TranslatePipe } from '../../../../core/i18n/translate.pipe';
import { Navbar } from '../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../shared/components/sidebar/sidebar';
import { Icon, IconName } from '../../../../shared/components/icon/icon';
import { dailySales, DailySale } from './dashboard-data';

type DashboardState = 'loading' | 'ready' | 'empty' | 'error';
interface RankedClient {
  posicion: number;
  id_cliente: number;
  nombre: string;
  total_comprado: number;
  cantidad_compras: number;
  ticket_promedio: number;
  ultima_compra: string | null;
  categoria: string;
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
interface DashboardData {
  empresa?: { id_empresa: number; nombre: string };
  indicadores?: {
    ventas_hoy?: number;
    ticket_promedio?: number;
    cajas_abiertas?: number;
    producto_estrella?: { nombre: string; unidades: number } | null;
    productos_bajo_stock?: number;
    productos_agotados?: number;
    ingresos_dia?: number;
    egresos_dia?: number;
    flujo_neto?: number;
  };
  evolucion_ventas_30_dias?: { puntos?: DailySale[]; total_periodo?: number; pico_diario?: number };
  ranking_clientes?: RankedClient[];
  recomendaciones_ia?: Recommendation[];
  generado_en?: string;
}
interface Metric {
  title: string;
  value: string;
  icon: IconName;
  note: string;
  tone: 'blue' | 'green' | 'amber';
  link: string[] | null;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MoneyPipe, TranslatePipe, Navbar, Sidebar, Icon],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit, OnDestroy {
  protected readonly Math = Math;
  protected readonly preferences = inject(PreferencesService);
  private readonly route = inject(ActivatedRoute);
  private readonly companyService = inject(CompanyService);
  protected readonly permissions = inject(CompanyPermissionsService);
  private readonly wsService = inject(CompanyWebSocketService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly subscriptions = new Subscription();
  private loadTimeout?: ReturnType<typeof setTimeout>;

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected companyName = 'Mi empresa';
  protected uiState: DashboardState = 'loading';
  protected connection: 'idle' | 'connecting' | 'connected' | 'error' = 'idle';
  protected data: DashboardData | null = null;
  protected period = 30;
  protected selectedDate: string | null = null;

  ngOnInit(): void {
    this.startTimeout();
    this.subscriptions.add(
      this.wsService.messages$.subscribe((message) => {
        if (message?.tipo === 'dashboard') {
          const payload = Array.isArray(message.datos) ? message.datos[0] : message.datos;
          if (payload?.empresa?.id_empresa && String(payload.empresa.id_empresa) !== this.companyId)
            return;
          this.data = payload ?? null;
          this.uiState = payload ? 'ready' : 'empty';
          if (payload?.empresa?.nombre) this.companyName = payload.empresa.nombre;
          clearTimeout(this.loadTimeout);
          this.cdr.markForCheck();
        } else if (message?.tipo === 'error') {
          this.uiState = 'error';
          this.cdr.markForCheck();
        }
      }),
    );
    this.subscriptions.add(
      this.wsService.connection$.subscribe((state) => {
        this.connection = state;
        if (state === 'error' && !this.data) this.uiState = 'error';
        this.cdr.markForCheck();
      }),
    );
    if (this.companyId) {
      this.subscriptions.add(
        this.companyService.obtenerEmpresa(this.companyId).subscribe({
          next: (company) => {
            this.companyName = company.nombre;
            this.cdr.markForCheck();
          },
          error: () => {
            /* La conexion del dashboard presenta su propio estado recuperable. */
          },
        }),
      );
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    clearTimeout(this.loadTimeout);
  }

  protected retry(): void {
    this.uiState = this.data ? 'ready' : 'loading';
    this.startTimeout();
    this.wsService.reconnect();
  }

  private startTimeout(): void {
    clearTimeout(this.loadTimeout);
    this.loadTimeout = setTimeout(() => {
      if (!this.data) {
        this.uiState = 'error';
        this.cdr.markForCheck();
      }
    }, 15000);
  }

  protected get today(): string {
    return new Intl.DateTimeFormat('en-CA', { timeZone: this.preferences.timeZone() }).format(
      new Date(),
    );
  }

  protected get todayLabel(): string {
    return new Intl.DateTimeFormat('es-BO', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      timeZone: this.preferences.timeZone(),
    }).format(new Date());
  }

  protected number(value: unknown): number {
    const result = Number(value ?? 0);
    return Number.isFinite(result) ? result : 0;
  }

  protected get metrics(): Metric[] {
    const ind = this.data?.indicadores;
    return [
      {
        title: 'Ventas de hoy',
        value: this.preferences.formatMoney(this.number(ind?.ventas_hoy)),
        icon: 'orders',
        note: 'Importe vendido en el día',
        tone: 'blue',
        link: this.reportLink,
      },
      {
        title: 'Ticket promedio',
        value: this.preferences.formatMoney(this.number(ind?.ticket_promedio)),
        icon: 'receipt',
        note: 'Promedio por venta de hoy',
        tone: 'blue',
        link: this.reportLink,
      },
      {
        title: 'Cajas abiertas',
        value: String(this.number(ind?.cajas_abiertas)),
        icon: 'wallet',
        note: 'Sesiones en operación',
        tone: 'green',
        link: this.branchLink,
      },
      {
        title: 'Alertas de inventario',
        value: String(this.stockAlerts),
        icon: 'boxes',
        note: 'Stock bajo o agotado',
        tone: this.stockAlerts ? 'amber' : 'green',
        link: this.productLink,
      },
    ];
  }

  protected get reportLink(): string[] | null {
    return this.permissions.hasPermission('REPORTE_GENERAR')
      ? ['/company', this.companyId, 'reports', 'static']
      : null;
  }
  protected get branchLink(): string[] | null {
    return this.permissions.hasPermission('SUCURSAL_VER')
      ? ['/company', this.companyId, 'branches']
      : null;
  }
  protected get productLink(): string[] | null {
    return this.permissions.hasPermission('PRODUCTO_VER')
      ? ['/company', this.companyId, 'products']
      : null;
  }
  protected get lowStock(): number {
    return this.number(this.data?.indicadores?.productos_bajo_stock);
  }
  protected get outOfStock(): number {
    return this.number(this.data?.indicadores?.productos_agotados);
  }
  protected get stockAlerts(): number {
    return this.lowStock + this.outOfStock;
  }
  protected get lowStockShare(): number {
    return this.stockAlerts ? (this.lowStock / this.stockAlerts) * 100 : 0;
  }
  protected get cashMetrics(): Array<{ title: string; value: number; icon: IconName }> {
    const ind = this.data?.indicadores;
    return [
      { title: 'Ingresos del día', value: this.number(ind?.ingresos_dia), icon: 'downLeft' },
      { title: 'Egresos del día', value: this.number(ind?.egresos_dia), icon: 'upRight' },
      { title: 'Flujo neto', value: this.number(ind?.flujo_neto), icon: 'wallet' },
    ];
  }
  protected get topProduct(): { nombre: string; unidades: number } | null {
    return this.data?.indicadores?.producto_estrella ?? null;
  }
  protected get rankingClientes(): RankedClient[] {
    return Array.isArray(this.data?.ranking_clientes) ? this.data.ranking_clientes : [];
  }
  protected get recomendaciones(): Recommendation[] {
    return Array.isArray(this.data?.recomendaciones_ia) ? this.data.recomendaciones_ia : [];
  }
  protected get series(): DailySale[] {
    return dailySales(this.data?.evolucion_ventas_30_dias?.puntos ?? [], this.period, this.today);
  }
  protected get periodTotal(): number {
    return this.series.reduce((sum, item) => sum + item.total, 0);
  }
  protected get periodPeak(): number {
    return Math.max(0, ...this.series.map((point) => point.total));
  }
  protected get hasSales(): boolean {
    return this.series.some((point) => point.total !== 0);
  }
  protected setPeriod(event: Event): void {
    this.period = Number((event.target as HTMLSelectElement).value);
    this.selectedDate = null;
  }
  protected get chartPoints(): Array<DailySale & { x: number; y: number }> {
    const series = this.series;
    const min = Math.min(0, ...series.map((point) => point.total));
    const max = Math.max(1, ...series.map((point) => point.total));
    return series.map((point, index) => ({
      ...point,
      x: 10 + (index / Math.max(1, series.length - 1)) * 620,
      y: 200 - ((point.total - min) / (max - min)) * 180,
    }));
  }
  protected get linePath(): string {
    return this.chartPoints
      .map((point, index) => (index === 0 ? 'M' : 'L') + point.x + ',' + point.y)
      .join(' ');
  }
  protected get areaPath(): string {
    return this.linePath + ' L630,210 L10,210 Z';
  }
  protected get selectedPoint(): DailySale | null {
    return this.series.find((point) => point.fecha === this.selectedDate) ?? null;
  }
  protected dateLabel(date: string): string {
    return new Intl.DateTimeFormat('es-BO', {
      day: 'numeric',
      month: 'short',
      timeZone: 'UTC',
    }).format(new Date(date + 'T12:00:00Z'));
  }
  protected axisLabel(fraction: number): string {
    const values = this.series.map((point) => point.total);
    const min = Math.min(0, ...values);
    const max = Math.max(1, ...values);
    return new Intl.NumberFormat('es-BO', { notation: 'compact', maximumFractionDigits: 1 }).format(
      min + (max - min) * fraction,
    );
  }
}
