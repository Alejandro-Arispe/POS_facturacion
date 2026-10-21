# POS Analytics (microservicio Python)

Servicio de inteligencia de negocio del POS. Usa **pandas** y **numpy** para calcular indicadores,
tendencia, pronóstico de ventas con estacionalidad semanal, análisis ABC de productos, patrones por
día/hora y recomendaciones.

El frontend no lo llama directamente: el backend Spring valida permisos, extrae las ventas de la empresa
y llama a `POST /analyze` (ver `AnaliticaService`).

## Ejecutar en local

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --port 8001
```

El backend lo busca en `ANALYTICS_URL` (por defecto `http://localhost:8001`).
Opcional: `ANALYTICS_API_KEY` en ambos servicios para que solo el backend pueda llamarlo.
