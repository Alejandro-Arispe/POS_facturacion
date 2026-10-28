import { watchReadPermissions } from '../../../../../core/services/watch-read-permissions';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  AnalyticsGroup,
  AnalyticsResult,
  AnalyticsService,
} from '../../../../../core/services/analytics.service';
import { Branch, CompanyService } from '../../../../../core/services/company.service';
import { PreferencesService } from '../../../../../core/services/preferences.service';
import { MoneyPipe } from '../../../../../shared/pipes/money.pipe';
import { Navbar } from '../../../../../shared/components/navbar/navbar';
import { Sidebar } from '../../../../../shared/components/sidebar/sidebar';

interface TimePoint {
  date: string;
  x: number;
  revenue: number | null;
  movingAvg: number | null;
  forecast: number | null;
  low: number | null;
  high: number | null;
}

interface Tooltip {
  x: number;
  flip: boolean;
  title: string;
  lines: string[];
}

const WEEKDAY_SHORT = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

/**
 * Dashboard de inteligencia de negocio. Los calculos (tendencia, pronostico, ABC, recomendaciones)
 * los hace el microservicio Python; esta pantalla solo los dibuja.
 */
@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [FormsModule, Navbar, Sidebar, MoneyPipe],
  templateUrl: './analytics.html',
  styleUrl: './analytics.css',
})
export class AnalyticsDashboard implements OnInit {
  private readonly readSnapshot=watchReadPermissions(() => { this.data=null;this.points=[];this.cdr.markForCheck(); });
  private readonly route = inject(ActivatedRoute);
  private readonly analyticsService = inject(AnalyticsService);
  private readonly companyService = inject(CompanyService);
  private readonly preferences = inject(PreferencesService);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly companyId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly weekdayShort = WEEKDAY_SHORT;
  protected readonly branchIdOf = branchIdOf;
  protected days = 30;
  protected branchId = '';
  protected branches: Branch[] = [];
  protected data: AnalyticsResult | null = null;
  protected loading = false;
  protected error = '';
  protected showTable = false;
  protected tooltip: Tooltip | null = null;
  protected hoverIndex: number | null = null;

  // Geometria del grafico de linea (coordenadas del viewBox)
  protected readonly lineW = 760;
  protected readonly lineH = 260;
  protected readonly pad = { top: 16, right: 16, bottom: 28, left: 64 };

  protected points: TimePoint[] = [];
  protected yTicks: number[] = [];
  protected xLabels: Array<{ x: number; label: string }> = [];
  protected revenuePath = '';
  protected avgPath = '';
  protected forecastPath = '';
  protected bandPath = '';
  protected todayX = 0;
  private yMax = 1;

  ngOnInit(): void {
    this.companyService.getSucursales(this.companyId).subscribe({
      next: (branches) => {
        this.branches = branches;
        this.cdr.detectChanges();
      },
      error: () => undefined,
    });
    this.load();
  }

  protected load(): void {
    this.loading = true;
    this.error = '';
    this.analyticsService.analyze(this.companyId, this.days, this.branchId || null).subscribe({
      next: (data) => {
        this.data = data;
        this.buildLineChart(data);
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.error = err?.error?.detail ?? 'No se pudo obtener el análisis.';
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  protected money(value: number | null | undefined): string {
    return this.preferences.formatMoney(value ?? 0);
  }

  protected growthClass(value: number | null): string {
    if (value === null) return '';
    return value >= 0 ? 'up' : 'down';
  }

  protected formatDate(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
  }

  // ---------- Grafico de linea: ventas, media movil y pronostico ----------
  private buildLineChart(data: AnalyticsResult): void {
    const daily = data.daily;
    const forecast = data.forecast;
    const total = daily.length + forecast.length;
    if (total === 0) {
      this.points = [];
      return;
    }
    const innerW = this.lineW - this.pad.left - this.pad.right;
    const step = total > 1 ? innerW / (total - 1) : 0;
    const x = (i: number) => this.pad.left + i * step;

    this.points = [
      ...daily.map((d, i) => ({
        date: d.date,
        x: x(i),
        revenue: d.revenue,
        movingAvg: d.moving_avg,
        forecast: null,
        low: null,
        high: null,
      })),
      ...forecast.map((f, i) => ({
        date: f.date,
        x: x(daily.length + i),
        revenue: null,
        movingAvg: null,
        forecast: f.revenue,
        low: f.low,
        high: f.high,
      })),
    ];

    const maxValue = Math.max(
      1,
      ...daily.map((d) => d.revenue),
      ...forecast.map((f) => f.high),
    );
    this.yTicks = this.niceTicks(maxValue);
    this.yMax = this.yTicks[this.yTicks.length - 1];

    const path = (values: Array<{ x: number; v: number | null }>) => {
      let d = '';
      let pen = false;
      for (const p of values) {
        if (p.v === null) {
          pen = false;
          continue;
        }
        d += `${pen ? 'L' : 'M'}${p.x.toFixed(1)},${this.y(p.v).toFixed(1)}`;
        pen = true;
      }
      return d;
    };
    this.revenuePath = path(this.points.map((p) => ({ x: p.x, v: p.revenue })));
    this.avgPath = path(this.points.map((p) => ({ x: p.x, v: p.movingAvg })));

    // El pronostico arranca en el ultimo dia real para que la linea sea continua
    const last = this.points[daily.length - 1];
    const fc = this.points.filter((p) => p.forecast !== null);
    this.forecastPath = last
      ? path([{ x: last.x, v: last.revenue }, ...fc.map((p) => ({ x: p.x, v: p.forecast }))])
      : '';
    this.bandPath = fc.length
      ? `M${fc.map((p) => `${p.x.toFixed(1)},${this.y(p.high ?? 0).toFixed(1)}`).join('L')}` +
        `L${[...fc].reverse().map((p) => `${p.x.toFixed(1)},${this.y(p.low ?? 0).toFixed(1)}`).join('L')}Z`
      : '';
    this.todayX = last ? last.x : this.pad.left;

    const labelEvery = Math.max(1, Math.ceil(total / 7));
    this.xLabels = this.points
      .filter((_, i) => i % labelEvery === Math.floor(labelEvery / 2))
      .map((p) => ({ x: p.x, label: this.shortDate(p.date) }));
  }

  protected y(value: number): number {
    const innerH = this.lineH - this.pad.top - this.pad.bottom;
    return this.pad.top + innerH - (value / this.yMax) * innerH;
  }

  protected onLineMove(event: MouseEvent, svg: Element): void {
    if (!this.points.length) return;
    const rect = svg.getBoundingClientRect();
    const vx = ((event.clientX - rect.left) / rect.width) * this.lineW;
    let nearest = 0;
    let best = Infinity;
    this.points.forEach((p, i) => {
      const dist = Math.abs(p.x - vx);
      if (dist < best) {
        best = dist;
        nearest = i;
      }
    });
    const p = this.points[nearest];
    this.hoverIndex = nearest;
    const lines: string[] = [];
    if (p.revenue !== null) lines.push(`Ventas: ${this.money(p.revenue)}`);
    if (p.movingAvg !== null) lines.push(`Media 7 días: ${this.money(p.movingAvg)}`);
    if (p.forecast !== null) {
      lines.push(`Pronóstico: ${this.money(p.forecast)}`);
      lines.push(`Rango probable: ${this.money(p.low)} – ${this.money(p.high)}`);
    }
    this.tooltip = {
      x: (p.x / this.lineW) * 100,
      flip: p.x > this.lineW * 0.6,
      title: this.formatDate(p.date),
      lines,
    };
  }

  protected clearHover(): void {
    this.hoverIndex = null;
    this.tooltip = null;
  }

  // ---------- Barras ----------
  protected maxOf(groups: Array<{ revenue: number }>): number {
    return Math.max(1, ...groups.map((g) => g.revenue));
  }

  protected barPct(value: number, groups: Array<{ revenue: number }>): number {
    return (value / this.maxOf(groups)) * 100;
  }

  protected bestWeekday(groups: AnalyticsGroup[]): string {
    return groups.reduce((a, b) => (b.revenue > a.revenue ? b : a), groups[0])?.label ?? '';
  }

  // ---------- Mapa de calor ----------
  protected get heatHours(): number[] {
    const hours = this.data?.heatmap.map((c) => c.hour) ?? [];
    if (!hours.length) return [];
    const min = Math.min(...hours);
    const max = Math.max(...hours);
    return Array.from({ length: max - min + 1 }, (_, i) => min + i);
  }

  protected heatValue(weekday: number, hour: number): number {
    return this.data?.heatmap.find((c) => c.weekday === weekday && c.hour === hour)?.sales ?? 0;
  }

  /** Escala secuencial de un solo tono (azul), de claro a oscuro. */
  protected heatColor(value: number): string {
    const max = Math.max(1, ...(this.data?.heatmap.map((c) => c.sales) ?? [1]));
    if (value === 0) return 'var(--viz-empty)';
    const steps = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'];
    const idx = Math.min(steps.length - 1, Math.floor((value / max) * (steps.length - 1) + 0.0001));
    return steps[idx];
  }

  private niceTicks(max: number): number[] {
    const rough = max / 4;
    const magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
    const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= rough) ?? rough;
    const ticks: number[] = [];
    for (let v = 0; v <= max + step * 0.999; v += step) {
      ticks.push(Math.round(v * 100) / 100);
    }
    return ticks;
  }

  protected shortDate(iso: string): string {
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }

  protected compact(value: number): string {
    if (value >= 1000) return `${(value / 1000).toFixed(value >= 10000 ? 0 : 1)}k`;
    return String(Math.round(value));
  }
}

export function branchIdOf(branch: Branch): string {
  return String(branch.id_sucursal ?? branch.idSucursal ?? branch.id ?? '');
}
