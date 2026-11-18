# HU y criterios de aceptación (fuente: POS_Facturacion.pdf, Sprints 1-4 + Especial)
Formato de cada HU: C = criterios de aceptación, R = reglas de negocio.
HU 14, 15 y 27 no tienen ficha formal en el PDF; sus criterios salen del Sprint Backlog y de las pruebas.

## HU-01 Registro de usuario
C: captura nombre, apellido, correo, contraseña; contraseña ≥6 con mayúscula, minúscula y número; asignar rol; correo único.
R: contraseña encriptada (hash); formato de correo válido. Extras del sprint: código de verificación, recuperación de contraseña.

## HU-02 Autenticación
C: pide correo y contraseña; redirige a la vista según rol; deniega credenciales incorrectas.
R: el token expira (JWT); guard de rutas en frontend.

## HU-03 Empresas y sucursales
C: registrar NIT, razón social, fecha de registro; asociar sucursales a la empresa; editar empresa.
R: NIT único (validar NIT boliviano); una empresa tiene varias sucursales; el registro asigna automáticamente plan de prueba de 30 días.

## HU-04 Registro de productos
C: nombre, descripción, precio de venta, stock inicial; categoría; imagen referencial; listado con búsqueda y filtro por categoría.
R: sin precios ni stock negativos; ID único autogenerado; editar y eliminar; código de barras.

## HU-05 Gestión de inventario
C: pantalla "Estado de Inventario" con stock actual de todos los productos; consulta rápida del catálogo.
R: el stock se actualiza solo con cada movimiento (venta, ingreso, ajuste).

## HU-06 Movimientos de inventario
C: 5 tipos de movimiento (entradas y salidas); registro correcto.
R: solo sobre productos ya creados; el movimiento actualiza el stock.

## HU-07 Alerta de stock mínimo
C: tabla de estado; notificación push en primer plano; resaltado en rojo si stock ≤ mínimo; configurar stock mínimo en la ficha del producto.
R: comparación stock_actual ≤ stock_minimo.

## HU-08 Clasificar clientes
C: crear, editar, eliminar categorías de cliente (nombre obligatorio y descripción); asignar categoría desde la ficha del cliente.
R: una categoría puede tener descuento (y límite de crédito en el modelo).

## HU-09 Ventas a crédito
C: opción "Crédito" en el PDV obliga a elegir cliente; si excede el límite, bloquea y pide autorización del admin.
R: solo clientes con límite asignado; no afecta el efectivo de caja; actualiza el saldo del cliente; la autorización queda registrada.

## HU-10 Control de saldos
C: pantalla "Cuentas por Cobrar" con clientes con saldo; detalle de facturas pendientes; registrar pago y descontar saldo; historial de pagos por cliente.
R: pagos parciales o totales; método de pago: efectivo, transferencia o QR.

## HU-11 Historial de ventas
C: pestaña de historial en la ficha del cliente con fecha, total y nº de factura; filtro por rango de fechas; modal de detalle (productos, cantidades, precios unitarios).
R: solo ventas COMPLETADA (no anuladas); detalle de solo lectura. Nota: el backlog v2 lo redefine como "visualizar ventas de la jornada" (lista general de ventas).

## HU-12 Ventas de productos
C: PDV con búsqueda por código o nombre; carrito dinámico (cantidad, precio, subtotal); cálculo de subtotal, IVA y total; aplicar descuento; bloquear venta si no hay stock.
R: descuento de stock en transacción atómica; el descuento de la categoría del cliente se aplica solo al elegir cliente; la venta guarda cajero, sucursal, fecha y hora; no se vende sin caja abierta.

## HU-13 Métodos de pago
C: efectivo, tarjeta y QR; pagos mixtos.
R: la suma de pagos = total de la venta (al contado).

## HU-14 Anulación de ventas (sin ficha)
C: endpoint de anulación; revierte stock; motivo obligatorio; botón "Anular" visible solo con permiso.
R: la venta anulada no aparece en historial ni reportes; la acción queda en bitácora.

## HU-15 Devolución de productos (sin ficha)
C: devolución parcial o total; valida plazo máximo de devolución.
R: impacta caja e inventario; reembolso en efectivo o crédito a favor.

## HU-16 Facturación electrónica
C: al finalizar la venta genera factura XML según estándar SIN; cola de contingencia con reintentos; consulta de estado en el SIN; reenvío de rechazadas; notifica al admin si hay rechazo; muestra estado de factura en el historial de ventas.
R: estructura exigida por el SIN; IVA 13%.

## HU-17 Validación de facturas
C: valida NIT y certificado de la empresa, cálculo de IVA y códigos SIN de productos; mensajes de error claros en el PDV.
R: se ejecuta automáticamente antes de enviar al SIN; si falla, no se envía y se muestra el error.

## HU-18 Envío de facturas
C: envía el PDF al correo del cliente tras emisión exitosa; correo opcional si el cliente no tiene; reintentos hasta 3 veces.
R: reenvío manual desde el historial de ventas.

## HU-19 Apertura de caja
C: al iniciar sesión el cajero verifica si ya tiene caja abierta; modal "Apertura de Caja" con Monto Inicial (y Nota).
R: una sola sesión abierta por cajero; solo rol empleado/cajero (no admin); sin caja abierta no se puede vender.

## HU-20 Cierre de caja
C: pantalla con resumen (ventas en efectivo, QR, ingresos, egresos); efectivo esperado = inicial + ingresos − egresos (+ ventas en efectivo); ingresa efectivo real y muestra la diferencia sobrante/faltante (con motivo); al confirmar cierra y libera al cajero.
R: se permite cerrar sin ventas; cerrada la caja no se vende hasta nueva apertura.

## HU-21 Movimientos de caja
C: botones "Ingreso" y "Egreso" en el PDV solo con caja abierta; formulario con tipo, monto y descripción; afecta el efectivo esperado.
R: descripción obligatoria; no se pueden editar ni eliminar.

## HU-22 Reporte de ventas
C: filtros obligatorios (fechas, sucursal, cajero, categoría) y nada se muestra sin ellos; tabla con fecha, factura, cliente, cajero, total; gráfico de ventas por día; totalizadores de ventas netas y nº de transacciones.
R: excluye ventas anuladas; reportes estáticos y dinámicos.

## HU-23 Reporte de inventario
C: filtros obligatorios (categoría, sucursal); tabla con producto, categoría, stock, precio, valor total (valorización); rotación (más y menos vendidos).
R: no incluye productos desactivados.

## HU-24 Exportación de reportes
C: botones "Exportar PDF" y "Exportar Excel" en todos los reportes; el archivo respeta los filtros; el PDF incluye logo, título, filtros y fecha.
R: se puede exportar sin datos; solo con permiso "exportar reportes".

## HU-25 Predicción de ventas (IA)
C: tipo de reporte nuevo por sucursal; top 5 productos con mayor proyección; widget en el Dashboard con predicción a 7 días; modelo de series temporales; reentrenamiento semanal.
R: usa datos históricos de ventas.

## HU-26 Recomendación de productos (IA)
C: sección "Los clientes también compraron…" en el PDV; hasta 3 sugerencias; agregar con un clic; algoritmo Apriori; solo productos con stock.
R: se genera al buscar/agregar un producto; si hay pocos datos, no se muestra.

## HU-27 Reconocimiento de productos (sin ficha)
C: botón "Escanear por cámara" en el PDV; envía imagen al modelo (CNN); devuelve producto o lista de coincidencias si confianza >80%.
R: mensaje "Producto no identificado" si la confianza es baja.

## HU-28 Reportes de caja
C: filtros obligatorios (fechas, sucursal, cajero); lista de sesiones cerradas con cajero, fecha, monto inicial, ventas, diferencia; detalle de movimientos de una sesión; exportación PDF/Excel.
R: las sesiones abiertas no aparecen.

## HU-29 Planes de suscripción
C: CRUD de planes Básico/Estándar/Premium (nombre, precio, duración, módulos, límites); super admin asigna plan; vista "Mi Plan" para el admin de empresa; restricciones por plan (middleware).
R: plan vencido → empresa "suspendida".

## HU-30 Gestión de roles
C: CRUD de roles personalizados con checkboxes de permisos; asignar rol desde la ficha del usuario; validación de permiso específico en backend; el frontend oculta botones según permisos.
R: ADMIN y CAJERO no se eliminan (sí se editan sus permisos); cambiar permisos de un rol se propaga a todos sus usuarios.

## HU-31 Bitácora
C: pantalla visible solo para SUPER_ADMIN; filtros por fecha, usuario, empresa, acción; cada registro con fecha/hora, usuario, empresa, acción, descripción, IP, módulo; tabla paginada.
R: registro automático (login ok/fallido, CRUD, anular venta, abrir/cerrar caja, cambio de plan); registros inmodificables e ineliminables.

## HU-32 Backup
C: botón "Generar Backup" en la ficha de empresa, solo super admin; incluye productos, clientes, ventas, facturas, movimientos de caja; lista de backups por empresa con fecha; descarga; restauración con confirmación explícita.
R: backup por empresa, no del sistema completo; backup y restauración se registran en bitácora.

## HU-33 Dashboard
C: widgets: ventas del día, nº de transacciones, top 5 productos, stock bajo, ventas por sucursal; gráficos de barras o líneas; datos al refrescar; pantalla principal del admin tras login.
R: nivel empresa (todas las sucursales salvo filtro); respeta la empresa del usuario.

## HU-34 Código de barras
C: escanear código existente agrega el producto con cantidad 1 (si ya está en el carrito, +1); código inexistente → "Producto no encontrado".
R: producto con stock 0 → "Stock insuficiente"; se procesa sin clic extra.

## Fallas que el propio equipo ya reportó (revisar primero)
- HU-12: el descuento de la categoría del cliente no se aplicaba en la venta.
- HU-11: la visualización del historial en la ficha del cliente figuraba "incompleto".
- HU-01: contraseña con poca seguridad; HU-04: búsqueda y filtro por categoría pendientes.
- Dashboard: gráfico "Ventas por sucursal" quedó básico.
