import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface AnalyticsKpis {
  revenue: number;
  sales: number;
  units: number;
  avg_ticket: number;
  items_per_sale: number;
  revenue_growth_pct: number | null;
  sales_growth_pct: number | null;
  best_day: string | null;
  best_day_revenue: number;
}

export interface AnalyticsDailyPoint {
  date: string;
  revenue: number;
  sales: number;
  moving_avg: number | null;
}

export interface AnalyticsForecastPoint {
  date: string;
  revenue: number;
  low: number;
  high: number;
}

export interface AnalyticsGroup {
  label: string;
  revenue: number;
  sales: number;
  share_pct: number;
}

export interface AnalyticsProduct {
  product: string;
  category: string | null;
  units: number;
  revenue: number;
  share_pct: number;
  cumulative_pct: number;
  abc_class: 'A' | 'B' | 'C';
}

export interface AnalyticsInsight {
  level: 'success' | 'warning' | 'info' | 'tip';
  title: string;
  detail: string;
}

export interface AnalyticsResult {
  company: string;
  currency: string;
  start: string;
  end: string;
  kpis: AnalyticsKpis;
  daily: AnalyticsDailyPoint[];
  forecast: AnalyticsForecastPoint[];
  forecast_total: number;
  trend: { direction: string; daily_change: number; pct_per_week: number; r2: number };
  products: AnalyticsProduct[];
  categories: AnalyticsGroup[];
  branches: AnalyticsGroup[];
  payment_methods: AnalyticsGroup[];
  weekdays: AnalyticsGroup[];
  heatmap: Array<{ weekday: number; hour: number; sales: number }>;
  insights: AnalyticsInsight[];
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly api = inject(ApiService);

  analyze(companyId: string | number, days: number, branchId?: string | number | null): Observable<AnalyticsResult> {
    const branch = branchId ? `&id_sucursal=${branchId}` : '';
    return this.api.get<AnalyticsResult>(`/api/reportes/${companyId}/analitica?dias=${days}${branch}`);
  }
}
