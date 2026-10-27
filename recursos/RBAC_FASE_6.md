# RBAC — fase 6: operaciones comerciales

La fase 6 integra ventas, cajas, inventario, pedidos y administración del negocio
con el resolver central. Conserva las operaciones existentes; no incorpora
anulaciones, devoluciones ni emisión fiscal que no estuvieran implementadas.

## 6A. Ventas

`VentaService` exige el permiso de la acción en la empresa y sucursal de la sesión.
La sesión debe pertenecer al operador y estar abierta para vender o registrar un
cobro. El backend vuelve a consultar roles, permisos, plan y pertenencia en cada
solicitud; no confía en los nombres de rol del JWT.

| Acción | Permiso |
|---|---|
| Historial de la sesión del operador | `sale:read` |
| Registrar venta | `sale:create` |
| Cambiar el precio respecto al catálogo | `sale:create` + `sale:create:precio_unitario` |
| Aplicar descuento de detalle o global | `sale:create` + `sale:discount` + `sale:create:descuento` |
| Cobro de crédito ya existente | `sale:collect` |
| Convertir un pedido en venta | `sale:create` + `order:manage`, con Pedidos habilitado por el plan |

`SalePricing` valida los detalles antes de persistir. El precio omitido se toma
del catálogo; enviar ese mismo precio no requiere el permiso de precio especial.
Las cantidades deben ser enteras positivas, no se admiten productos repetidos y
los importes son no negativos, con hasta dos decimales y dentro de la precisión
persistida. Productos inactivos o de otra empresa se rechazan.

Se calculan en servidor el importe bruto, neto por línea, subtotal y total. Un
`subtotal` o `total` suministrado debe coincidir con el cálculo; de lo contrario
se responde 400. `descuento_total` representa el descuento solicitado, incluidos
los descuentos de las líneas; requiere su autorización y no puede ser menor que
su suma ni mayor que el subtotal. El subtotal de una línea conserva el contrato
anterior: su importe neto después del descuento.

Los pagos positivos no pueden repetir método y deben sumar el total calculado.
Una venta al contado con total positivo requiere pagos. El estado de la venta se
deriva en servidor. La venta, detalles, salida de inventario y entradas de caja
comparten transacción: una falla de stock revierte todo. Los movimientos
obligatorios de caja deben estar configurados; no se omiten silenciosamente.

La venta a crédito existente valida el límite disponible y sus cobros no pueden
superar el saldo. El saldo del cliente deriva de ventas/cobros y no puede
modificarse desde un formulario de clientes. No se agregaron pantallas de crédito.

La conversión verifica empresa, sucursal, cliente, estado, productos y cantidades
del pedido. Se bloquea su fila y se impide facturarlo dos veces. Marcar un pedido
como convertido mediante el endpoint genérico de estados se rechaza.

`VentaCreate` no tiene un cálculo de impuestos ni una operación de emisión fiscal.
Esta entrega no introduce una tasa tributaria ni genera facturas nuevas.
`factura_linea=true` exige `invoice:issue`, conservando la capacidad preexistente.
La revisión de comprobantes, archivos y canales de facturación corresponde a fase 7.

## 6B. Cajas

Crear, leer, editar, abrir y cerrar cajas requieren respectivamente
`cash_register:create/read/update/open/close` en su sucursal. Abrir una caja
inactiva o con monto negativo se rechaza. La apertura y su movimiento se guardan
juntos. Se bloquean usuario/caja durante la apertura y sesión durante las
operaciones que cambian sus saldos para coordinar ventas, movimientos y cierre.

Los movimientos manuales tienen permisos propios:

- `cash_movement:read`: listado y resumen por método.
- `cash_movement:create`: ingreso, egreso y ajustes manuales.

Los permisos de inventario no habilitan movimientos de caja. Apertura y cierre
solo se generan desde sus operaciones específicas. Para escribir o cerrar una
sesión debe ser su operador; la lectura autorizada de movimientos puede hacerse
por un supervisor de esa sucursal. No se añadió una operación de toma de control.

Al cerrar, el monto esperado procede de los movimientos almacenados y la
diferencia es `monto_real - monto_esperado`. Los valores derivados enviados por
el navegador se contrastan; no se guardan como fuente de verdad. No se permiten
métodos repetidos ni omitir métodos presentes en el resumen. Un resumen obsoleto
produce 400 y debe actualizarse.

## 6C. Inventario

| Operación | Permiso en la sucursal |
|---|---|
| Consultar existencias | `inventory:read` |
| Configurar mínimo/máximo | `inventory:configure` |
| Historial de movimientos | `inventory_movement:read` |
| Movimiento manual | `inventory_movement:create` |

Los límites no negativos se validan conjuntamente; máximo no puede ser menor que
mínimo. Ese endpoint rechaza atributos desconocidos, incluido un intento de
cambiar `cantidad`. Las cantidades cambian mediante movimientos, con producto de
la empresa, dirección válida, existencia suficiente y comprobación de desborde.

La salida automática `Venta` tiene un camino interno que requiere `sale:create`
y fija el tipo y la cantidad desde los detalles ya validados. No tiene endpoint
propio y no permite registrar otros tipos. El endpoint manual rechaza ese tipo:
un cajero no necesita poder hacer ajustes para vender. Las escrituras del stock
se coordinan mediante bloqueo del producto; las ventas ordenan sus productos
por ID para reducir conflictos al tomar varios bloqueos.

## 6D. Personal, clientes, pedidos y configuración

Se conservan las reglas de asignación, delegación, roles protegidos e invitación
de personal de fases 1–3. Los listados de personal filtran relaciones de otras
sucursales. Los listados de empresas/sucursales de empleados incluyen roles
personalizados activos, sin exigir que se llamen EMPLEADO.

Clientes y categorías requieren sus acciones `client:*` y `client_category:*`.
Invitar clientes y cambiar la empresa/configuración requieren concesiones con
ámbito de empresa. Un permiso de una sucursal no se presta a una asignación de
cliente global para editar todo el negocio. Editar una sucursal requiere
`branch:update` en esa sucursal; crearla exige `branch:create` de empresa.

`Cliente.idEmpresa` separa el perfil comercial de una misma cuenta en distintos
negocios, con clave foránea e índice. Venta, edición, pedidos y listados seleccionan
el perfil de esa empresa, en lugar del primer cliente asociado al usuario.
Cambiar la categoría no puede cambiar de empresa. El cliente puede consultar sus
propios perfiles/pedidos mientras conserve acceso; las modificaciones comerciales
requieren permisos administrativos y no se habilitan por ser la misma cuenta.

La invitación de cliente usa JWT firmado con tipo específico, vencimiento y un
registro consumible. Al aceptar se vuelve a comprobar el permiso del emisor.
El token no funciona como sesión de acceso. El enlace antiguo con IDs de empresa
y usuario ya no registra clientes: se deben emitir invitaciones nuevas.

Los pedidos de operadores se autorizan por sucursal y se filtran cuando se listan
sin sucursal. La gestión requiere `order:manage`. El portal del cliente valida
propiedad, perfil activo, empresa/sucursal y plan; no hereda permisos para gestionar
pedidos de otros clientes. Sus precios y descuentos estimados se calculan desde
el catálogo y la categoría, con redondeo a dos decimales.

## Interfaz

Las rutas de operaciones declaran permisos y el guard consulta el contexto de la
sucursal. El menú separa movimientos de caja de inventario. El POS habilita el
precio especial y descuentos solo con sus permisos; al revocarlos en una sesión
abierta usa nuevamente el precio de catálogo y retira descuentos del payload.
Historiales/formularios se ocultan cuando se pierde la acción correspondiente.

Para usar el formulario actual de ventas también se necesita `inventory:read`
porque busca productos en las existencias de la sucursal. La selección de clientes
requiere `client:read`; el consumidor final sigue disponible. El resumen de cierre
de la interfaz necesita `cash_movement:read`, además del permiso para cerrar.
Estas dependencias no se conceden silenciosamente.

## Migración V3 y puesta en marcha

Reiniciar el backend con `ddl-auto: update` prepara `cliente.id_empresa`, su clave
foránea/índice y ejecuta V3 después de V1/V2. Sin actualización automática del
esquema, preparar antes el script `db/rbac/{postgresql,h2}/V003__client_scope.sql`.
Las conversiones y el marcador siguen a cargo de `RbacMigration`.

V3 incorpora `sale:create:precio_unitario`, `sale:create:descuento`,
`cash_movement:read`, `cash_movement:create` y `sale:collect`:

- El descuento deriva únicamente de la concesión histórica activa `sale:discount`.
- Leer movimientos de caja deriva de `cash_register:read`; crearlos deriva de
  `cash_register:update`. Se pueden conceder explícitamente a otros roles.
- Precio especial y cobros se conceden inicialmente solo al rol protegido de
  administrador que ya tenga `sale:create` activo.
- La delegación deriva de la misma concesión de origen. No se restauran
  concesiones existentes/revocadas y repetir el arranque no vuelve a migrar.

El perfil de cliente obtiene empresa desde su categoría histórica. Si no tiene
categoría, se convierte solo cuando hay una única empresa con asignación activa
del rol protegido CLIENTE. Los perfiles ambiguos conservan empresa nula y quedan
fuera de operaciones empresariales hasta revisar su correspondencia; no se asignan
a todos los negocios de la cuenta. El ensayo sobre una copia de PostgreSQL y la
revisión de esos datos existentes continúan siendo parte de fase 8.

## Verificación y alcance pendiente

Pruebas HTTP con H2 aislado cubren acciones permitidas/denegadas, precio/descuento,
importes adulterados, rollback, stock, cierre, empresa/sucursal, revocación con el
mismo JWT, conversión de pedidos, cobros e invitaciones. También se amplió el
fixture de migración para V3 y se probaron las restricciones del carrito en Angular.

Resultado: **74 pruebas de backend aprobadas**, incluidas **25 de operaciones**;
**50 pruebas de frontend aprobadas**. Empaquetado ejecutable y compilación de
producción correctos. El frontend mantiene el aviso de tamaño inicial de unos
1,48 MB frente al umbral de aviso de 600 kB. Las pruebas usan H2 y datos de fixture;
no modifican cuentas ni bases reales.

Comandos reproducibles:

- Backend: `./mvnw.cmd package`.
- Frontend: `npm test -- --watch=false` y `npm run build`.

Reportes, dashboard, exportaciones, comprobantes, archivos y datos derivados por
otros canales siguen pendientes en fase 7. Las respuestas auxiliares de inventario
no constituyen todavía una garantía global de filtrado de campos de producto en
todos los canales. El ensayo de concurrencia integral, PostgreSQL y la retirada
de adaptadores pertenecen a fase 8. Esta entrega no declara completa la auditoría
de seguridad del POS.

## Continuación

La cobertura posterior de reportes, archivos y canales en vivo se documenta en
[RBAC_FASE_7.md](RBAC_FASE_7.md). Los pendientes generales continúan en fase 8.
