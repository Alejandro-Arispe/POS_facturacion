# Registro y planes de la demostración

Flujo: `/planes` → cuenta y verificación → Stripe de prueba → acceso.
El alta tiene cuatro pasos. No se solicita una empresa ni se muestra una pantalla
de confirmación antes de abrir Stripe. El plan Gratuito se registra sin tarjeta
y continúa directamente a la confirmación.

| Plan | Precio BOB | Usuarios, incluido el dueño | Sucursales | Cajas por sucursal | Productos activos |
|---|---:|---:|---:|---:|---:|
| Gratuito | 0 | 2 | 1 | 1 | 100 |
| Básico | 99 | 5 | 1 | 2 | 1.000 |
| Pro | 199 | 15 | 3 | 3 | 10.000 |

No existen cuotas mensuales de ventas. Los clientes no consumen plazas de personal.
Gratuito incluye resumen diario; Básico añade reportes estándar; Pro añade reportes
avanzados, pedidos y predicciones. Créditos y cobranzas no diferencian planes.

El catálogo añade Gratuito y migra únicamente los precios originales de Básico
(29.99 → 99 BOB) y Pro (59.99 → 199 BOB). Premium se conserva para suscripciones
históricas y el seeder; no se ofrece en la página pública.

## Pago antes de crear la empresa

Una compra de plan pertenece al usuario verificado y se guarda en `compra_plan`.
El pago pendiente no habilita el POS. Una vez confirmado, aparece como disponible
en **Mis empresas**, incluso al volver a iniciar sesión desde otra pestaña.

El usuario puede crear su empresa después: el formulario asocia la compra disponible
en la misma transacción que registra la empresa. Una compra solo puede utilizarse
una vez y exclusivamente por su dueño. Los 30 días de uso comienzan al asociarla
a la empresa. Gratuito no vence. La configuración de sucursales y productos sigue
dentro del POS, fuera del asistente de alta.

Las empresas anteriores conservan sus suscripciones y la opción de cambiar de
plan desde Mis empresas. Ese cambio sigue asociado a la empresa existente.

## Stripe de prueba

Configurar `STRIPE_SECRET_KEY` con una clave **sk_test_** en el entorno del backend
o en `pos-backend/.env`, y reiniciar el backend. No poner claves secretas en Angular.
`app.stripe.test-only` rechaza claves de producción. Si falta una clave, se muestra
un error recuperable; no se activa automáticamente un plan de pago.

Stripe Checkout cobra en `bob`. Para la prueba interactiva usar:

- Tarjeta `4242 4242 4242 4242`.
- Fecha futura, por ejemplo `12/34`.
- CVC `123` y datos ficticios.

Es un pago único por 30 días; no hay renovación automática. El retorno consulta el
estado del servidor y un HTTP exitoso con pago pendiente no concede acceso.
También se mantiene `/api/pagos/webhook`; para reenviar eventos hay que configurar
`STRIPE_WEBHOOK_SECRET`. Los eventos repetidos no duplican la compra ni revierten
cambios de plan. Si se cierra Stripe antes de volver, al retomar el mismo plan se
consulta la sesión pendiente para recuperar un pago aprobado sin pagar otra vez.

## URL de retorno

El frontend manda `frontend_origin` al crear el checkout, normalmente
`http://localhost:4200` en desarrollo. El backend lo valida contra los orígenes
permitidos de `CORS_ORIGINS` y el origen configurado. No acepta destinos arbitrarios.

Las rutas de retorno son `/payment/success?session_id=...` y `/payment/cancel`.
Las rutas anteriores `/administrator/payment/success` y `/administrator/payment/cancel`
siguen funcionando para sesiones antiguas.

`FRONTEND_BASE_URL` es el origen de respaldo si un cliente no manda su origen, y
también se usa en los enlaces de correo. En el entorno local debe ser
`http://localhost:4200`; en despliegue debe ser el dominio del frontend desplegado.
Las sesiones de Stripe ya creadas conservan las URLs con las que se generaron.

## Correo y validación

Se reutiliza SMTP: código de verificación y bienvenida con correo de acceso y enlace
a login. No se envía la contraseña elegida por email. El perfil local conserva
la activación automática existente si SMTP falla.

`mvnw.cmd test` usa H2 temporal, SMTP simulado y un servidor HTTP que reproduce
Stripe. `npm.cmd test -- --watch=false` prueba el salto directo al pago y la
confirmación de compras sin empresa. No se realizan cobros reales en estas pruebas.

