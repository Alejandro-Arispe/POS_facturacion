import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import { AnalyticsForecastPoint, AnalyticsProductForecast, AnalyticsService } from '../../../../core/services/analytics.service';
import { MoneyPipe } from '../../../../shared/pipes/money.pipe';

/**
 * HU-25: prediccion de ventas a 7 dias en el dashboard. Usa el microservicio de analitica (Python),
 * que ajusta una serie temporal (tendencia + estacionalidad semanal) con los ultimos 60 dias de ventas.
 */
@Component({
  selector: 'app-prediction-widget',
  standalone: true,
  imports: [MoneyPipe, DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="min-w-0 rounded-2xl border border-line bg-surface p-5 sm:p-6" aria-label="Predicción de ventas a 7 días">
      <header class="mb-4 flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 class="text-[15px] font-bold">Predicción de ventas · próximos 7 días</h2>
          <p class="text-xs text-muted">Serie temporal con tendencia y estacionalidad semanal (últimos 60 días)</p>
        </div>
        @if (!loading && !error && forecast.length) {
          <p class="text-right">
            <span class="block text-xs text-muted">Total proyectado</span>
            <strong class="text-lg" data-numeric>{{ total | money }}</strong>
          </p>
        }
      </header>

      @if (loading) {
        <p class="text-sm text-muted">Calculando predicción…</p>
      } @else if (error) {
        <p class="text-sm text-muted">{{ error }}</p>
      } @else if (!forecast.length) {
        <p class="text-sm text-muted">Aún no hay ventas suficientes para proyectar. Se necesita al menos una semana de historial.</p>
      } @else {
        <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <div class="flex h-40 items-end gap-2" role="list" aria-label="Ventas proyectadas por día">
            @for (point of forecast; track point.date) {
              <div class="flex min-w-0 flex-1 flex-col items-center gap-1" role="listitem"
                [attr.aria-label]="(point.date | date: 'EEEE d') + ': ' + (point.revenue | money)">
                <span class="text-[10px] text-muted" data-numeric>{{ point.revenue | number: '1.0-0' }}</span>
                <div class="w-full rounded-t-md bg-brand" [style.height.%]="barHeight(point)" [title]="point.revenue | money"></div>
                <span class="text-[11px] text-muted">{{ point.date | date: 'EEE d' }}</span>
              </div>
            }
          </div>
          <div class="min-w-0">
            <h3 class="mb-2 text-sm font-semibold">Top 5 productos con mayor proyección</h3>
            <ol class="grid gap-2">
              @for (item of products; track item.product; let i = $index) {
                <li class="flex items-center justify-between gap-3 text-sm">
                  <span class="min-w-0 truncate"><strong>{{ i + 1 }}.</strong> {{ item.product }}</span>
                  <span class="shrink-0 text-right" data-numeric>
                    {{ item.units | number: '1.0-0' }} u · {{ item.revenue | money }}
                    @if (item.change_pct !== null) {
                      <small [class.text-green-700]="item.change_pct >= 0" [class.text-red-700]="item.change_pct < 0">
                        {{ item.change_pct >= 0 ? '▲' : '▼' }} {{ item.change_pct | number: '1.0-0' }}%
                      </small>
                    }
                  </span>
                </li>
              }
            </ol>
          </div>
        </div>
      }
    </section>
  `,
})
export class PredictionWidget implements OnInit {
  private readonly analytics = inject(AnalyticsService);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input({ required: true }) companyId!: string;

  protected loading = true;
  protected error = '';
  protected forecast: AnalyticsForecastPoint[] = [];
  protected products: AnalyticsProductForecast[] = [];
  protected total = 0;
  private max = 1;

  ngOnInit(): void {
    this.analytics.analyze(this.companyId, 60, null, 7).subscribe({
      next: (result) => {
        this.forecast = result.forecast ?? [];
        this.products = result.product_forecast ?? [];
        this.total = result.forecast_total ?? 0;
        this.max = Math.max(1, ...this.forecast.map((p) => p.revenue));
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.loading = false;
        this.error = error?.status === 403
          ? 'La predicción está disponible en los planes con reportes avanzados.'
          : (error?.error?.detail ?? 'No se pudo calcular la predicción en este momento.');
        this.cdr.markForCheck();
      },
    });
  }

  protected barHeight(point: AnalyticsForecastPoint): number {
    return Math.max(4, (point.revenue / this.max) * 100);
  }
}
