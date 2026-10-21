from __future__ import annotations

from datetime import date, datetime

from pydantic import BaseModel, Field


class SaleLine(BaseModel):
    """Una linea de venta: un producto dentro de una venta."""

    sale_id: int
    timestamp: datetime
    branch: str | None = None
    product: str
    category: str | None = None
    quantity: float
    line_total: float
    sale_total: float
    payment_method: str | None = None


class StockItem(BaseModel):
    product: str
    branch: str | None = None
    quantity: float
    min_stock: float | None = None


class AnalyticsRequest(BaseModel):
    company: str = ""
    currency: str = "BOB"
    start: date
    end: date
    forecast_days: int = Field(default=14, ge=1, le=60)
    lines: list[SaleLine] = Field(default_factory=list)
    stock: list[StockItem] = Field(default_factory=list)


class Kpis(BaseModel):
    revenue: float
    sales: int
    units: float
    avg_ticket: float
    items_per_sale: float
    revenue_growth_pct: float | None
    sales_growth_pct: float | None
    best_day: str | None
    best_day_revenue: float


class DailyPoint(BaseModel):
    date: date
    revenue: float
    sales: int
    moving_avg: float | None


class ForecastPoint(BaseModel):
    date: date
    revenue: float
    low: float
    high: float


class Trend(BaseModel):
    direction: str
    daily_change: float
    pct_per_week: float
    r2: float


class ProductStat(BaseModel):
    product: str
    category: str | None
    units: float
    revenue: float
    share_pct: float
    cumulative_pct: float
    abc_class: str


class GroupStat(BaseModel):
    label: str
    revenue: float
    sales: int
    share_pct: float


class HeatCell(BaseModel):
    weekday: int
    hour: int
    sales: int


class Insight(BaseModel):
    level: str
    title: str
    detail: str


class AnalyticsResponse(BaseModel):
    company: str
    currency: str
    start: date
    end: date
    kpis: Kpis
    daily: list[DailyPoint]
    forecast: list[ForecastPoint]
    forecast_total: float
    trend: Trend
    products: list[ProductStat]
    categories: list[GroupStat]
    branches: list[GroupStat]
    payment_methods: list[GroupStat]
    weekdays: list[GroupStat]
    heatmap: list[HeatCell]
    insights: list[Insight]
