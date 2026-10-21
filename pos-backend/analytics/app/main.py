"""Microservicio de analitica de negocio del POS.

Recibe las lineas de venta de una empresa (el backend Spring las extrae y valida permisos) y devuelve
indicadores, tendencias, pronostico y recomendaciones calculadas con pandas/numpy.
"""

from __future__ import annotations

import os
from datetime import date

from fastapi import Depends, FastAPI, Header, HTTPException

from .analysis import analyze
from .schemas import AnalyticsRequest, AnalyticsResponse

API_KEY = os.getenv("ANALYTICS_API_KEY", "")

app = FastAPI(title="POS Analytics", version="1.0.0")


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    """Si se configuro ANALYTICS_API_KEY, solo el backend (que la conoce) puede llamar al servicio."""
    if API_KEY and x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="API key invalida")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "pos-analytics", "date": date.today().isoformat()}


@app.post("/analyze", response_model=AnalyticsResponse, dependencies=[Depends(require_api_key)])
def analyze_sales(request: AnalyticsRequest) -> AnalyticsResponse:
    return analyze(request)
