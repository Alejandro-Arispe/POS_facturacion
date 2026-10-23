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

## Datos de prueba para una cuenta existente

Desde `pos-backend`, ejecuta:

```powershell
.\seed-demo.ps1
```

Por defecto carga datos para `mococe5860@ofags.com`. Para otra cuenta:

```powershell
.\seed-demo.ps1 -Email 'tu-correo@example.com'
```

Usa la misma conexion PostgreSQL, variables `DB_*` y `.env` del backend.
Si trabajas con el perfil H2, agrega `-Local`; la cuenta debe existir en esa base.
Requiere Java 17 y acceso a las dependencias Maven. El comando termina al finalizar
y puede ejecutarse mientras el backend esta abierto porque usa un puerto temporal.

Crea **Mi tienda demo** y asigna la cuenta como administradora de esa empresa:

- Plan Premium de prueba por un año, sin operaciones con Stripe.
- 2 sucursales en La Paz y 2 cajas.
- 3 categorias, 3 subcategorias y 12 productos con precios y costos en BOB.
- 8 clientes ficticios, categorias Minorista y Mayorista y descuentos del 5% para mayoristas.
- Stock en ambas sucursales, incluidos productos agotados y con stock bajo.
- 120 ventas pagadas durante los 30 dias anteriores, con detalles, pagos en efectivo/QR/tarjeta,
  movimientos de inventario, aperturas y cierres de caja conciliados.

Inicia sesion con tu cuenta y selecciona **Mi tienda demo**. Para probar los reportes,
elige un rango que incluya los ultimos 30 dias. Las cajas quedan cerradas para que
puedas abrir una nueva sesion y registrar tus propias ventas.

El seeder no cambia la contraseña ni los datos existentes de la cuenta. Todo se inserta
en una transaccion: si falla, se revierte la carga completa. Una segunda ejecucion
detecta la empresa por su NIT `SEED-DEMO-<id_usuario>` y omite la carga, conservando
tambien las modificaciones que hayas hecho en la empresa de prueba. Los usuarios
ficticios tienen correos `example.invalid` y una contraseña aleatoria; no se envian correos.

Pruebas de integridad, repeticion y rollback con H2 en memoria:

```powershell
.\mvnw.cmd test
```

## Contrato con el frontend

- JSON en `snake_case`
- JWT Bearer (`access_token`, `token_type: bearer`)
- Errores: `{"detail":"..."}`
- CORS habilitado para Angular en `:4200`
