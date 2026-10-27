# RBAC — fase 7: canales de lectura, reportes y archivos

Esta entrega extiende la política a los canales alternativos. No añade un motor
nuevo de permisos ni concede capacidades automáticamente. Los permisos canónicos
existentes siguen siendo la fuente de autorización y se comprueban en servidor.

## Frontera compartida de datos

`DataReadPolicy` concentra el ámbito, los permisos de origen y las dependencias
de campos para reportes, analítica, snapshots y archivos. `FieldAuthorization`
sigue siendo el punto de proyección de productos; los canales auxiliares usan el
mismo `RbacResolver.field`, que exige acción general y permiso de atributo.

Una autorización para generar un reporte no concede lectura de productos,
ventas, clientes, caja o inventario. Tampoco concede automáticamente sus campos.
Los permisos se resuelven nuevamente por solicitud y antes de cada envío en vivo.

| Canal | Política aplicada |
|---|---|
| Reportes estándar y parametrizados | `report:generate` y lectura del recurso de origen con ámbito de empresa; campos usados por el reporte también legibles |
| Reportes dinámicos e interpretación | `report:generate`, plan avanzado y lectura de las fuentes utilizadas; la interpretación no ejecuta SQL libre |
| QBE | Catálogo, columnas, filtros, agrupaciones, agregaciones y orden sujetos a permisos; filas acotadas a empresa/sucursales autorizadas |
| Dashboard WebSocket | Sesión válida, `dashboard:read`, lectura de ventas/inventario/caja y nombre de producto; comprobación antes de cada envío |
| Analítica Python | Autorizar empresa/sucursal, plan y todos los campos de entrada antes de extraer/enviar datos |
| Stock y movimientos auxiliares | Omitir nombre, precio, imagen, código, unidad y estado de producto cuando su lectura esté restringida |
| Imágenes de productos | Sesión y lectura de producto/imagen vigentes; archivo vinculado a un producto autorizado |
| Facturas | Metadatos filtrados por sucursal; descargar el comprobante íntegro exige los permisos adicionales descritos abajo |
| Respaldos | Propietario protegido y lectura íntegra de productos/permisos de roles; restauración también exige acciones y campos de escritura |
| Notificaciones | Cuenta real, empresa/sucursal y contexto del evento; historial y marcar leído comprueban visibilidad de la fila |
| Bitácora | `audit:read` con ámbito de empresa; los registros técnicos no se prestan entre sucursales |

Las vistas que agregan toda la empresa exigen concesiones con ámbito de empresa.
Una asignación limitada a una sucursal no habilita el agregado global, aunque el
navegador omita el filtro. Para consultas de sucursal se dispone de QBE y de la
analítica con `id_sucursal`; no se entrega un total global que incluya otras sedes.

## QBE y datos derivados

Se incorporó `costo` al catálogo QBE de productos, usando el permiso ya existente
`product:read:costo`. No se inventan IDs ni se concede el campo por añadirlo al
catálogo. Las fuentes visibles y sus columnas predeterminadas se calculan según
la política actual.

Un campo protegido no puede usarse indirectamente en una condición, una suma,
un promedio, un grupo o un orden. Una consulta explícita no autorizada devuelve
403 antes de ejecutar SQL. Las columnas predeterminadas se limitan a las legibles.
`valor` de inventario depende de `product:read:precio`, porque se calcula como
cantidad por precio; prohibir solo la columna precio permitiría deducirla.

Para fuentes con sucursal, el SQL incorpora el conjunto de sucursales donde se
poseen tanto `report:generate` como la lectura de la fuente. Las capacidades de
campos se intersectan en ese conjunto para no usar el permiso de una sede en otra.
El catálogo compartido de productos conserva la unión de asignaciones definida
en fases anteriores. Clientes se filtra por `cliente.id_empresa`, evitando unir
las fichas de varios negocios de una misma cuenta.

Los campos actuales de catálogo y los importes históricos de ventas son recursos
distintos: los datos de una transacción siguen gobernados por `sale:read`; no se
presenta un importe histórico como el precio actual del producto. Las etiquetas
actuales de producto incluidas en ventas también respetan lectura por campo.
No existen métricas de ganancia/margen calculadas desde costo en los reportes
actuales; una futura métrica debe declarar sus dependencias en `DataReadPolicy`
antes de incorporarse al catálogo.

## Exportación de datos actuales

Los escritores PDF y Excel/XLS existentes continúan en Angular, pero antes de
crear el archivo vuelven a pedir el reporte al servidor. La solicitud lleva
`X-Report-Export: true`; el interceptor verifica `report:export` y el servicio
repite los controles de lectura, ámbito y campos. QBE exige también exportación
en las sucursales incluidas. Un rechazo descarta el resultado anterior y no
genera una descarga con datos guardados.

Las exportaciones dinámicas/QBE repiten la última consulta exitosa; las
parametrizadas reconstruyen sus filtros y procesan la respuesta nueva. Las
cadenas de texto de XLS neutralizan fórmulas. El CSV de productos continúa
usando la exportación del backend implementada en fases 4–5.

`watchReadPermissions` retira snapshots de reportes cuando cambia el conjunto de
permisos. Refrescar las mismas concesiones no borra la vista. Para QBE se vuelve
a abrir la consulta y se carga el catálogo autorizado después de un cambio.

El permiso de exportación controla la función de descarga de la aplicación; no
impide copiar información que ya fue autorizada para lectura. Tampoco puede
revocar copias que un usuario descargó cuando estaba autorizado.

## Archivos privados

Se retiraron los mapeos estáticos de los directorios `media-dir` y `xml-dir`.

- `GET /media/products/{archivo}` solo entrega una imagen con referencia vigente
  y lectura autorizada. Conocer la URL o conservar una imagen huérfana en disco
  no concede acceso. Rutas externas y archivos sin asociación no se sirven.
- `GET /api/facturas/{id}/xml` y `/pdf` comprueban la empresa/sucursal real de la
  venta. La lista de facturas devuelve estos enlaces, no el contenido XML ni una
  ubicación arbitraria del servidor.
- Un comprobante histórico íntegro puede incluir campos que no es seguro borrar
  de un PDF o XML firmado. Por eso su descarga exige `invoice:reprint`, `sale:read`
  y lectura de todos los campos de producto. Si se restringe costo u otro campo,
  se conservan los metadatos autorizados y se deniega el archivo completo.
- `/XMLS/**` y otras rutas de `/media/**` ya no exponen archivos por nombre.
  Las rutas protegidas verifican normalización, archivo regular y destino real,
  evitando salir de su directorio mediante rutas relativas o enlaces simbólicos.
- Las respuestas de archivo usan `Cache-Control: no-store` y `nosniff`. Angular
  obtiene los blobs mediante su API autenticada; `SecureSrc` libera la URL local
  y retira la imagen al perder permiso. Las facturas también se descargan por API.

Los enlaces antiguos a XML/PDF deben abrirse desde la lista de facturas actual.
No se añaden parámetros con tokens de acceso a las URL de archivos.

Los respaldos completos requieren lectura de todos los campos y de los permisos
de roles, aunque el actor sea propietario. La restauración exige además crear,
editar y eliminar productos, escribir sus campos, administrar sucursales/empresa
y las capacidades administrativas de roles ya previstas. Los conflictos de base
de datos no devuelven la fila ni sus valores ocultos en el mensaje de error.
Los respaldos programados siguen siendo una operación interna de mantenimiento;
no se publican sus directorios mediante HTTP.

## WebSocket y notificaciones

Abrir el socket no entrega datos. El cliente debe enviar primero:

```json
{"tipo":"autenticar","token":"<token de sesión>"}
```

El dashboard acepta también `id_sucursal` en ese mensaje. El backend verifica
firma, vencimiento, tipo de token y usuario activo; un token de invitación no es
una sesión. No se acepta la identidad escrita en la URL como autorización.
El dashboard revalida antes de cada actualización y cierra con 1008 cuando pierde
acceso. El frontend descarta el dashboard al cerrarse la conexión. Los orígenes
permitidos proceden de `app.cors.origins`.

El socket de clientes solo registra la suscripción después de validar que la
cuenta coincide con el usuario de la ruta. Antes de cada notificación se vuelve a
comprobar la sesión y visibilidad del evento. Los tokens no viajan en la URL.

El registro FCM usa la identidad de la sesión y valida empresa y asociación del
dispositivo. El rol escrito por el navegador no es una autoridad. La integración
FCM conserva su estado anterior de simulación; no se afirma que envíe push reales.

El historial no permite pedir mensajes privados de otra cuenta ni marcar filas
que el actor no puede consultar. Las alertas de inventario necesitan acceso a su
sucursal, inventario, alertas y nombre del producto. Los mensajes privados solo
se muestran al destinatario con acceso vigente a la empresa. Los títulos/textos
históricos se sustituyen por avisos genéricos y se proyecta una lista explícita
de claves de payload para evitar devolver texto libre o atributos desconocidos.
El endpoint genérico `send-alert` deja de permitir publicaciones arbitrarias;
las alertas se generan desde operaciones autorizadas del sistema.

## Servicio auxiliar y errores

El microservicio Python analiza exclusivamente los vectores recibidos: no se
identificó una conexión propia a la base del POS ni un historial de empresas
consultable desde su API. Spring valida los permisos antes de enviar líneas y
stock. Las pruebas usan un servidor HTTP efímero y verifican que no se envíe costo
ni stock de otra sucursal. No se requiere cambiar su contrato ni introducir otro
framework.

Los errores de integridad y los errores internos no devuelven detalles SQL ni
mensajes de excepciones con valores de filas. Se cerró el acceso HTTP a la consola
H2; los endpoints técnicos públicos de salud y autenticación conservan su función.

## Puesta en marcha, pruebas y pendientes

Reiniciar backend y frontend carga estas políticas y el nuevo primer mensaje del
WebSocket. La fase 7 reutiliza el catálogo existente y no requiere una migración
V4 ni ejecutar scripts nuevos; las migraciones V1–V3 conservan su comportamiento.

Las comprobaciones usan H2 y datos aislados, archivos bajo `target` y un servidor
HTTP local de prueba; no se modifican cuentas ni bases de datos reales.

Resultado final: **97 pruebas de backend aprobadas**, incluidas **23 de canales
de lectura**, y **57 pruebas de frontend aprobadas** en 17 archivos. Empaquetado
Java y compilación de producción correctos. Se conserva el aviso del frontend de
unos **1,49 MB** frente al umbral de aviso existente de 600 kB; no se modificaron
los presupuestos para ocultarlo.

Comandos reproducibles:

- Backend: `./mvnw.cmd package`.
- Frontend: `npm test -- --watch=false` y `npm run build`.

El cierre posterior de la fase 8, con PostgreSQL, recuperación, concurrencia,
rendimiento y retirada de adaptadores, se documenta en [RBAC_FASE_8.md](RBAC_FASE_8.md).
Las cifras anteriores corresponden a la entrega de fase 7. Los recursos futuros
deben registrar sus permisos y dependencias; la cobertura no se aplica automáticamente.
