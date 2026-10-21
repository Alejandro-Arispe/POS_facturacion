# POS Backend — Spring Boot

Migración del backend FastAPI (`pos-backend/pos-backend-main`) a Spring Boot 4 / Java 17.

El frontend Angular en `http://localhost:4200` sigue usando los **mismos paths** (`/api/auth`, `/api/empresas`, etc.). Este servicio escucha en el **puerto 8000**, igual que uvicorn.

## Requisitos

- Java 17+
- Maven Wrapper incluido (`mvnw.cmd`) — no hace falta instalar Maven
- PostgreSQL (`pos_db`) para el perfil por defecto, o H2 con el perfil `local`

## Configuración

Variables (mismas que el backend Python):

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pos_db
DB_USER=postgres
DB_PASSWORD=7722794
JWT_SECRET_KEY=changeme-super-secret-key-change-in-production-32b
CORS_ORIGINS=http://localhost:4200,http://127.0.0.1:4200
```

## Arranque local (H2)

```powershell
cd pos-backend-spring
.\mvnw.cmd -DskipTests "-Dspring-boot.run.profiles=local" spring-boot:run
```
si ya tengo base en postgres: 
.\mvnw.cmd spring-boot:run

## Arranque con PostgreSQL

Configura `DB_PASSWORD` (y el resto de `DB_*`) y luego:

```powershell
.\mvnw.cmd spring-boot:run
```

Healthcheck: `GET http://localhost:8000/` → `{"status":"ok"}`

## Contrato con el frontend

- JSON en `snake_case`
- JWT Bearer (`access_token`, `token_type: bearer`)
- Errores: `{"detail":"..."}`
- CORS habilitado para Angular en `:4200`
