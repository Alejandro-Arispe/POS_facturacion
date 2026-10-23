# POS — Contexto del producto

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

El usuario confirmó que el proyecto es una **demo académica para presentar y
evaluar el sistema**. No se ha confirmado su uso comercial ni un tipo concreto
de comercio como público objetivo.

La implementación distingue administradores de empresa, empleados, clientes y
un superadministrador. Estos son roles existentes en el código; las personas y
dispositivos concretos que participarán en la evaluación quedan por definir.

## Product Purpose

Demostrar el funcionamiento de un sistema de punto de venta y gestión comercial
con empresas, sucursales, cajas, productos, inventario, clientes y reportes.

La necesidad expresada en esta sesión es disponer de datos ficticios para probar
los flujos del sistema. Los criterios formales de evaluación académica todavía
no se han proporcionado.

## Operating Context

- Repositorio con frontend Angular en `pos-frontend` y backend Spring Boot/Java
  en `pos-backend`.
- Desarrollo local: el frontend usa habitualmente el puerto 4200 y el backend
  el 8000. El frontend se inicia con `npm start` desde `pos-frontend`; el backend,
  con `.\mvnw.cmd spring-boot:run` desde `pos-backend`.
- PostgreSQL es la base configurada por defecto; el perfil `local` usa H2.
- Existe una empresa de demostración, **Mi tienda demo**, generada durante esta
  sesión para realizar pruebas. Sus clientes, productos y ventas son ficticios.
- Los requisitos adicionales se revisarán después, según indicó el usuario.

## Capabilities and Constraints

Capacidades observadas en las rutas, vistas y servicios actuales:

- Registro e inicio de sesión; acceso por roles y permisos.
- Gestión de empresas, sucursales y personal.
- Apertura y cierre de cajas, ventas y formas de pago.
- Catálogo de productos, categorías, inventario y movimientos por sucursal.
- Clientes, categorías de clientes, crédito y cobranzas.
- Pedidos, comprobantes/facturas, reportes, paneles y auditoría.
- Planes de suscripción y funciones de superadministración.

La interfaz actual está principalmente en español. Los valores por defecto del
backend incluyen idioma `es`, moneda `BOB` y zona horaria `America/La_Paz`. Son
hechos de la implementación, **no requisitos ratificados por el usuario**.

Decisiones abiertas, aplazadas explícitamente:

- Público comercial futuro y rubro del negocio.
- Dispositivos, navegadores y condiciones de conectividad que deben soportarse.
- Requisitos fiscales, normativos y de facturación. La presencia de comprobantes
  en el código no demuestra certificación ni integración tributaria oficial.
- Nombre comercial definitivo, compromisos de marca y elementos obligatorios.
- Requisitos específicos de accesibilidad y criterios de evaluación.

## Evidence on Hand

- Rutas y módulos: `pos-frontend/src/app/app.routes.ts`.
- Vistas operativas: `pos-frontend/src/app/features/companies`.
- Página de presentación existente:
  `pos-frontend/src/app/features/landing/home_page`.
- Recursos gráficos existentes: `pos-frontend/public/hero-pos.jpg`,
  `pos-frontend/public/login-side.jpg` y `pos-frontend/public/favicon.ico`.
- Imagen adicional en `recursos/original-b27b9754366211fc9df46536899f9471.webp`.
  Su finalidad como referencia o activo de marca no se ha confirmado.
- Seeder: `pos-backend/seed-demo.ps1`, documentado en `pos-backend/README.md`.
  Genera 2 sucursales, 2 cajas, 12 productos, 8 clientes y 120 ventas de los
  30 días anteriores, con pagos e inventario conciliados.
- Pruebas del seeder:
  `pos-backend/src/test/java/com/pos/common/seed/DemoAccountSeederTests.java`.
- No se han proporcionado testimonios, métricas de clientes reales ni evidencia
  de certificaciones o resultados comerciales.

## Product Principles

Principios derivados del propósito académico y del trabajo solicitado:

1. Facilitar la demostración y comprobación de los flujos implementados.
2. Mantener distinguibles los datos ficticios y la evidencia de uso real.
3. Conservar coherencia entre ventas, pagos, caja e inventario al preparar pruebas.
4. Registrar las decisiones pendientes sin convertirlas en requisitos confirmados.

La preferencia sobre cómo iniciar futuros trabajos visuales se guarda en
`.impeccable/config.json`, separada del contexto del producto.
