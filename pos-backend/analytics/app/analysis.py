"""Calculos de inteligencia de negocio con pandas y numpy."""

from __future__ import annotations

from datetime import date, timedelta

import numpy as np
import pandas as pd

from .schemas import (
    AnalyticsRequest,
    AnalyticsResponse,
    DailyPoint,
    ForecastPoint,
    GroupStat,
    HeatCell,
    Insight,
    Kpis,
    ProductStat,
    Trend,
)

WEEKDAYS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"]
SYMBOLS = {"BOB": "Bs", "USD": "$", "EUR": "€", "PEN": "S/", "ARS": "AR$", "CLP": "CLP$", "MXN": "MX$", "BRL": "R$"}


def _money(value: float, currency: str) -> str:
    return f"{SYMBOLS.get(currency, currency)} {value:,.2f}"


def _pct(current: float, previous: float) -> float | None:
    if previous <= 0:
        return None
    return round((current - previous) / previous * 100, 1)


def _empty_response(req: AnalyticsRequest) -> AnalyticsResponse:
    return AnalyticsResponse(
        company=req.company,
        currency=req.currency,
        start=req.start,
        end=req.end,
        kpis=Kpis(
            revenue=0, sales=0, units=0, avg_ticket=0, items_per_sale=0,
            revenue_growth_pct=None, sales_growth_pct=None, best_day=None, best_day_revenue=0,
        ),
        daily=[], forecast=[], forecast_total=0,
        trend=Trend(direction="sin datos", daily_change=0, pct_per_week=0, r2=0),
        products=[], categories=[], branches=[], payment_methods=[], weekdays=[], heatmap=[],
        insights=[Insight(
            level="info",
            title="Aún no hay ventas en el periodo",
            detail="Registra ventas en el punto de venta para que el análisis pueda calcular tendencias y pronósticos.",
        )],
    )


def _group(df: pd.DataFrame, column: str, total: float) -> list[GroupStat]:
    if df.empty:
        return []
    grouped = (
        df.fillna({column: "Sin dato"})
        .groupby(column)
        .agg(revenue=("sale_total", "sum"), sales=("sale_id", "nunique"))
        .sort_values("revenue", ascending=False)
    )
    return [
        GroupStat(
            label=str(label),
            revenue=round(float(row.revenue), 2),
            sales=int(row.sales),
            share_pct=round(float(row.revenue) / total * 100, 1) if total else 0,
        )
        for label, row in grouped.iterrows()
    ]


CREDIT_LABEL = "Crédito (pendiente de cobro)"


def _payment_methods(req: AnalyticsRequest, sales: pd.DataFrame, total: float) -> list[GroupStat]:
    """Reparte cada venta segun lo pagado con cada metodo; las ventas sin pagos son ventas a credito."""
    if sales.empty:
        return []
    sale_ids = set(sales["sale_id"])
    rows: list[dict] = []
    paid_ids: set[int] = set()
    for p in req.payments:
        if p.sale_id in sale_ids:
            rows.append({"label": p.method or "Sin dato", "revenue": p.amount, "sale_id": p.sale_id})
            paid_ids.add(p.sale_id)
    unpaid = sales[~sales["sale_id"].isin(paid_ids)]
    for _, sale in unpaid.iterrows():
        rows.append({"label": CREDIT_LABEL, "revenue": float(sale["sale_total"]), "sale_id": int(sale["sale_id"])})
    grouped = (
        pd.DataFrame(rows)
        .groupby("label")
        .agg(revenue=("revenue", "sum"), sales=("sale_id", "nunique"))
        .sort_values("revenue", ascending=False)
    )
    return [
        GroupStat(
            label=str(label),
            revenue=round(float(row.revenue), 2),
            sales=int(row.sales),
            share_pct=round(float(row.revenue) / total * 100, 1) if total else 0,
        )
        for label, row in grouped.iterrows()
    ]


def analyze(req: AnalyticsRequest) -> AnalyticsResponse:
    if not req.lines:
        return _empty_response(req)

    lines = pd.DataFrame([line.model_dump() for line in req.lines])
    lines["timestamp"] = pd.to_datetime(lines["timestamp"])
    lines["date"] = lines["timestamp"].dt.date

    period_days = (req.end - req.start).days + 1
    prev_start = req.start - timedelta(days=period_days)
    current = lines[(lines["date"] >= req.start) & (lines["date"] <= req.end)]
    previous = lines[(lines["date"] >= prev_start) & (lines["date"] < req.start)]
    if current.empty:
        return _empty_response(req)

    # Una fila por venta
    sales = (
        current.groupby("sale_id")
        .agg(
            timestamp=("timestamp", "first"),
            date=("date", "first"),
            sale_total=("sale_total", "first"),
            branch=("branch", "first"),
            units=("quantity", "sum"),
            items=("product", "count"),
        )
        .reset_index()
    )
    prev_sales = previous.groupby("sale_id").agg(sale_total=("sale_total", "first")).reset_index()

    revenue = float(sales["sale_total"].sum())
    n_sales = int(len(sales))
    prev_revenue = float(prev_sales["sale_total"].sum()) if not prev_sales.empty else 0.0

    # Serie diaria completa (dias sin ventas = 0) y media movil de 7 dias
    all_days = pd.date_range(req.start, req.end, freq="D").date
    daily = (
        sales.groupby("date")
        .agg(revenue=("sale_total", "sum"), sales=("sale_id", "count"))
        .reindex(all_days, fill_value=0)
    )
    daily.index.name = "date"
    daily["moving_avg"] = daily["revenue"].rolling(7, min_periods=3).mean()

    best_idx = daily["revenue"].idxmax()
    kpis = Kpis(
        revenue=round(revenue, 2),
        sales=n_sales,
        units=round(float(sales["units"].sum()), 2),
        avg_ticket=round(revenue / n_sales, 2) if n_sales else 0,
        items_per_sale=round(float(sales["items"].mean()), 2),
        revenue_growth_pct=_pct(revenue, prev_revenue),
        sales_growth_pct=_pct(n_sales, len(prev_sales)),
        best_day=best_idx.isoformat() if daily["revenue"].max() > 0 else None,
        best_day_revenue=round(float(daily["revenue"].max()), 2),
    )

    # Tendencia lineal (minimos cuadrados) sobre los ingresos diarios
    # El ultimo dia puede estar en curso (incompleto): no se usa para ajustar la tendencia.
    fit_daily = daily.iloc[:-1] if len(daily) > 7 and req.end >= date.today() else daily
    x = np.arange(len(fit_daily), dtype=float)
    y = fit_daily["revenue"].to_numpy(dtype=float)
    if len(fit_daily) >= 2 and y.std() > 0:
        slope, intercept = np.polyfit(x, y, 1)
        fitted = slope * x + intercept
        ss_res = float(((y - fitted) ** 2).sum())
        ss_tot = float(((y - y.mean()) ** 2).sum())
        r2 = 1 - ss_res / ss_tot if ss_tot else 0.0
    else:
        slope, intercept, fitted, r2 = 0.0, float(y.mean()), np.full_like(y, y.mean()), 0.0
    mean_rev = float(y.mean()) or 1.0
    pct_week = slope * 7 / mean_rev * 100
    # Solo se habla de tendencia si la recta explica una parte razonable de la variacion (R^2).
    if r2 < 0.15 or abs(pct_week) <= 2:
        direction = "estable"
    else:
        direction = "creciente" if pct_week > 0 else "decreciente"
    trend = Trend(direction=direction, daily_change=round(float(slope), 2), pct_per_week=round(float(pct_week), 1), r2=round(r2, 3))

    # Pronostico: tendencia * factor estacional del dia de la semana, con intervalo del 80 %
    weekday_series = pd.Series(y, index=pd.to_datetime(fit_daily.index)).groupby(lambda d: d.weekday()).mean()
    overall = float(weekday_series.mean()) or 1.0
    factors = {wd: (float(weekday_series.get(wd, overall)) / overall if overall else 1.0) for wd in range(7)}
    residual_std = float(np.std(y - fitted)) if len(y) > 2 else 0.0
    forecast: list[ForecastPoint] = []
    for i in range(1, req.forecast_days + 1):
        day = req.end + timedelta(days=i)
        base = max(0.0, slope * (len(fit_daily) - 1 + (day - fit_daily.index[-1]).days) + intercept)
        value = max(0.0, base * factors[day.weekday()])
        margin = 1.28 * residual_std
        forecast.append(ForecastPoint(
            date=day, revenue=round(value, 2), low=round(max(0.0, value - margin), 2), high=round(value + margin, 2),
        ))
    forecast_total = round(sum(p.revenue for p in forecast), 2)

    # Productos: analisis ABC (Pareto 80/15/5)
    products_df = (
        current.groupby("product")
        .agg(category=("category", "first"), units=("quantity", "sum"), revenue=("line_total", "sum"))
        .sort_values("revenue", ascending=False)
    )
    total_lines = float(products_df["revenue"].sum()) or 1.0
    products_df["share"] = products_df["revenue"] / total_lines * 100
    products_df["cumulative"] = products_df["share"].cumsum()
    products_df["abc"] = np.where(
        products_df["cumulative"] - products_df["share"] < 80, "A",
        np.where(products_df["cumulative"] - products_df["share"] < 95, "B", "C"),
    )
    products = [
        ProductStat(
            product=str(name),
            category=row.category,
            units=round(float(row.units), 2),
            revenue=round(float(row.revenue), 2),
            share_pct=round(float(row.share), 1),
            cumulative_pct=round(float(row.cumulative), 1),
            abc_class=str(row.abc),
        )
        for name, row in products_df.iterrows()
    ]

    categories_df = (
        current.fillna({"category": "Sin categoría"})
        .groupby("category")
        .agg(revenue=("line_total", "sum"), sales=("sale_id", "nunique"))
        .sort_values("revenue", ascending=False)
    )
    categories = [
        GroupStat(label=str(label), revenue=round(float(r.revenue), 2), sales=int(r.sales),
                  share_pct=round(float(r.revenue) / total_lines * 100, 1))
        for label, r in categories_df.iterrows()
    ]

    sales["weekday"] = pd.to_datetime(sales["timestamp"]).dt.weekday
    sales["hour"] = pd.to_datetime(sales["timestamp"]).dt.hour
    weekday_df = sales.groupby("weekday").agg(revenue=("sale_total", "sum"), sales=("sale_id", "count"))
    weekdays = [
        GroupStat(
            label=WEEKDAYS[wd],
            revenue=round(float(weekday_df.loc[wd, "revenue"]) if wd in weekday_df.index else 0, 2),
            sales=int(weekday_df.loc[wd, "sales"]) if wd in weekday_df.index else 0,
            share_pct=round(float(weekday_df.loc[wd, "revenue"]) / revenue * 100, 1) if wd in weekday_df.index and revenue else 0,
        )
        for wd in range(7)
    ]
    heat = sales.groupby(["weekday", "hour"]).size()
    heatmap = [HeatCell(weekday=int(wd), hour=int(h), sales=int(c)) for (wd, h), c in heat.items()]

    insights = _insights(req, kpis, trend, products, categories, weekdays, sales, current, forecast_total)

    return AnalyticsResponse(
        company=req.company,
        currency=req.currency,
        start=req.start,
        end=req.end,
        kpis=kpis,
        daily=[
            DailyPoint(
                date=d,
                revenue=round(float(r.revenue), 2),
                sales=int(r.sales),
                moving_avg=None if pd.isna(r.moving_avg) else round(float(r.moving_avg), 2),
            )
            for d, r in daily.iterrows()
        ],
        forecast=forecast,
        forecast_total=forecast_total,
        trend=trend,
        products=products,
        categories=categories,
        branches=_group(sales, "branch", revenue),
        payment_methods=_payment_methods(req, sales, revenue),
        weekdays=weekdays,
        heatmap=heatmap,
        insights=insights,
    )


def _insights(
    req: AnalyticsRequest,
    kpis: Kpis,
    trend: Trend,
    products: list[ProductStat],
    categories: list[GroupStat],
    weekdays: list[GroupStat],
    sales: pd.DataFrame,
    current: pd.DataFrame,
    forecast_total: float,
) -> list[Insight]:
    cur = req.currency
    out: list[Insight] = []

    if kpis.revenue_growth_pct is not None:
        if kpis.revenue_growth_pct >= 5:
            out.append(Insight(level="success", title=f"Ingresos +{kpis.revenue_growth_pct}% vs. periodo anterior",
                               detail="Las ventas crecen respecto al periodo anterior de igual duración."))
        elif kpis.revenue_growth_pct <= -5:
            out.append(Insight(level="warning", title=f"Ingresos {kpis.revenue_growth_pct}% vs. periodo anterior",
                               detail="Las ventas bajaron; revisa precios, stock de productos clave y promociones."))

    out.append(Insight(
        level="info",
        title=(f"Tendencia {trend.direction} ({trend.pct_per_week:+.1f}% por semana)"
               if trend.direction != "estable" else "Ventas estables, sin una tendencia marcada"),
        detail=f"Se proyectan {_money(forecast_total, cur)} en ventas para los próximos {req.forecast_days} días.",
    ))

    a_products = [p for p in products if p.abc_class == "A"]
    if products and a_products:
        out.append(Insight(
            level="info",
            title=f"{len(a_products)} de {len(products)} productos generan el 80% de los ingresos",
            detail="Productos clase A: " + ", ".join(p.product for p in a_products[:5])
            + ". Prioriza su disponibilidad y exhibición.",
        ))
    c_products = [p for p in products if p.abc_class == "C"]
    if c_products:
        out.append(Insight(
            level="tip",
            title=f"{len(c_products)} productos de baja rotación (clase C)",
            detail="Considera promociones o combos para: " + ", ".join(p.product for p in c_products[:4]) + ".",
        ))

    if categories:
        out.append(Insight(level="info", title=f"La categoría líder es {categories[0].label}",
                           detail=f"Aporta el {categories[0].share_pct}% de los ingresos del periodo."))

    best = max(weekdays, key=lambda w: w.revenue)
    worst = min((w for w in weekdays if w.sales > 0), key=lambda w: w.revenue, default=None)
    if best.revenue > 0:
        detail = f"Es el día con más ingresos ({best.share_pct}% del total)."
        if worst and worst.label != best.label:
            detail += f" El más flojo es {worst.label}: buen día para promociones."
        out.append(Insight(level="tip", title=f"{best.label} es tu mejor día", detail=detail))

    if not sales.empty:
        peak_hour = int(sales.groupby("hour").size().idxmax())
        out.append(Insight(level="tip", title=f"Hora pico: {peak_hour}:00 – {peak_hour + 1}:00",
                           detail="Asegura personal y cajas abiertas en ese horario."))

    # Cobertura de stock: dias que alcanza el inventario al ritmo de venta actual
    if req.stock:
        days = max(1, (req.end - req.start).days + 1)
        daily_units = current.groupby("product")["quantity"].sum() / days
        stock = pd.DataFrame([s.model_dump() for s in req.stock]).groupby("product")["quantity"].sum()
        risky = []
        for product, units in daily_units.items():
            if units <= 0 or product not in stock.index:
                continue
            coverage = float(stock[product]) / float(units)
            if coverage < 7:
                risky.append((product, coverage))
        risky.sort(key=lambda item: item[1])
        if risky:
            out.append(Insight(
                level="warning",
                title=f"{len(risky)} producto(s) se agotarían en menos de 7 días",
                detail="; ".join(f"{p}: {c:.1f} días" for p, c in risky[:5]) + ". Programa su reposición.",
            ))
    return out
