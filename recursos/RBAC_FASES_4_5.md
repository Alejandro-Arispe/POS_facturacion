# RBAC: productos y editor visual — fases 4–5

La autorización definitiva permanece en el backend. Esta entrega aplica el piloto
al catálogo de productos y sustituye el selector plano de roles por una matriz
construida con el catálogo real. No añade frameworks ni modifica el flujo de pagos.

## Permisos y migración V2

Los campos de producto protegidos son `nombre`, `descripcion`, `codigo_barra`,
`id_subcategoria`, `unidad_medida`, `precio`, `costo`, `imagen` y `activo`.
Cada uno tiene permisos `product:read:campo`, `product:create:campo` y
`product:update:campo`. Para usarlo se exige también la acción general
correspondiente. `product:delete` es una acción independiente.

Los identificadores `id_producto` e `id_empresa` son metadatos de identificación,
no entradas modificables. La relación `subcategoria` solo se incluye cuando se
permite leer `id_subcategoria`; no constituye un acceso alternativo a esa asociación.
Las existencias se gestionan mediante inventario: enviar `stock` al editar un
producto devuelve 400.

`RbacMigration` ejecuta V1 y después V2 durante el arranque, usando la tabla
`rbac_migration`. V2 no cambia tablas de productos: registra los permisos nuevos y
convierte las capacidades que antes heredaban el permiso general.

- Conserva IDs y concesiones existentes, incluidas las revocadas.
- No restaura permisos financieros de costo/precio.
- Solo concede automáticamente los campos nuevos que antes heredaban una acción
  general activa. La posibilidad de delegarlos se deriva de esa misma concesión.
- Un reinicio posterior no repite la conversión ni restaura permisos retirados.
- Los estados históricos `EDITABLE`, `SOLO_LECTURA`, `OCULTO` ya se convierten en
  V1. El adaptador anterior queda únicamente para compatibilidad; las pantallas de
  productos y el editor nuevo consultan los códigos canónicos.

Con `ddl-auto: update`, reiniciar el backend aplica la migración. Para preparar
externamente el esquema inicial siguen disponibles los scripts V1 de PostgreSQL
y H2. No hay que insertar marcadores manualmente. Antes de aplicar sobre una base
existente corresponde respaldarla; el ensayo automatizado usa H2 y fixtures del
modelo anterior. El ensayo de despliegue sobre una copia de PostgreSQL sigue pendiente.

## Contrato de productos

| Operación | Endpoint |
|---|---|
| Listado de una empresa | `GET /api/empresas/{empresa}/productos` |
| Listado de empresas legibles del usuario | `GET /api/productos` |
| Detalle | `GET /api/productos/{producto}` |
| Crear con JSON | `POST /api/empresas/{empresa}/productos` |
| Crear con imagen | `POST /api/empresas/{empresa}/productos/con-imagen` |
| Actualización parcial con JSON | `PUT /api/productos/{producto}` |
| Actualizar datos e imagen juntos | `PUT /api/productos/{producto}/con-imagen` |
| Actualizar solo imagen | `PUT /api/productos/{producto}/imagen` |
| Eliminar | `DELETE /api/productos/{producto}` |
| Exportar catálogo | `POST /api/empresas/{empresa}/productos/exportar` |

Las respuestas contienen únicamente claves autorizadas. Un dato oculto se omite;
no se devuelve como `null`. Un campo nullable autorizado conserva su clave y su
valor `null`. Las respuestas de creación/edición tampoco conceden lectura: un
usuario que solo puede escribir recibe los identificadores y cualquier campo que
esté autorizado a leer.

Las actualizaciones distinguen tres casos:

1. Campo ausente: conservar el valor anterior.
2. Campo presente con `null`: comprobar su permiso y después su nulabilidad.
3. Campo presente con valor: comprobar su permiso y validar el valor.

Si aparece un campo protegido sin autorización, toda la solicitud devuelve 403 y
se revierte. No se ignora el campo ni se guarda el resto. Se rechazan con 400 los
atributos desconocidos, importes negativos, más de dos decimales, textos demasiado
largos o valores nulos en campos obligatorios. Una subcategoría ajena devuelve 404.

Para crear se necesitan nombre, subcategoría, unidad y precio, junto con sus
permisos de creación. Costo es opcional y el estado predeterminado es activo.
Los importes usan BOB en la interfaz. Cambiar estado o imagen requiere su permiso
específico; no basta con `product:update`.

La imagen se envía como archivo. Un `imagen: null` autorizado retira la referencia;
una URL arbitraria en el JSON se rechaza. Los formularios nuevos envían una parte
multipart `datos` con JSON y la parte `imagen`, conservando los `null` explícitos.
Se mantiene compatibilidad con campos multipart individuales. No se admiten
campos o archivos repetidos ni mezclar ambos formatos de datos.

Los permisos y valores se comprueban antes de guardar el archivo. Datos e imagen
se guardan en una sola transacción; un rollback elimina el archivo nuevo. Un
producto referenciado devuelve 409 al eliminar y puede desactivarse si se tiene
permiso para editar su estado.

El CSV se genera en el servidor con la política vigente al exportar. Su cuerpo
recibe `columnas` e `ids`; el servidor filtra columnas y filas autorizadas de la
empresa, escapa separadores/comillas y neutraliza fórmulas. Una columna protegida
revocada desaparece también de la cabecera. No se exportan valores antiguos del
almacenamiento del navegador.

## Editor de roles

La lista permite buscar por nombre/descripción y filtrar activos/inactivos. Los
roles de sistema se identifican como protegidos. El backend comunica por rol
`protegido` y `administrable`, y expone capacidades del actor en
`GET /api/roles/empresa/{empresa}/capacidades`.

El formulario reúne nombre, descripción, estado, permisos y delegación en un solo
guardado transaccional. Usa los atributos `recurso`, `accion` y `atributo` del
catálogo: agrupa por módulo/recurso y separa acceso general de campos. Las acciones
no registradas se muestran como guiones; no se crean IDs para completar la tabla.

La búsqueda no altera la selección. Hay contadores, carga, errores que conservan
el borrador, confirmación de guardado y controles deshabilitados fuera de la
capacidad de concesión. “Configurar delegación” muestra un segundo control para
permitir que el rol conceda ese permiso a otros. Usar y delegar son capacidades
separadas. Las dependencias se advierten sin seleccionar lectura o escritura
automáticamente, incluidos los campos obligatorios para crear productos.

Los roles personalizados se asignan desde la pantalla de personal existente, que
consulta únicamente roles asignables. Sus acciones reflejan también `role:assign`.
Los límites por empresa/sucursal y las invitaciones siguen validados en el servidor.

## Formularios y sesiones abiertas

`ProductFieldEditor` es el componente compartido de alta/edición. Solo muestra
valores legibles y permite entradas según la acción correspondiente. Un permiso
de escritura sin lectura muestra un control vacío para establecer un valor nuevo,
sin rellenarlo con el valor restringido. Las actualizaciones envían únicamente
campos modificados y autorizados.

`CompanyPermissionsService.hasField` centraliza las comprobaciones de recurso y
campo. Mientras una empresa está abierta, el contexto se refresca cada 30 segundos
y al volver a la ventana; cambiar empresa/sucursal o cerrar sesión cancela el
contexto anterior. Un fallo de refresco deniega capacidades. El servidor revoca
acceso en la siguiente solicitud, sin esperar al refresco visual.

Las columnas, búsquedas, formularios y CSV no reutilizan valores que hayan quedado
fuera de las capacidades actuales. Incluso conservando escritura, perder lectura
retira el valor previamente cargado del control; una entrada nueva del propio
usuario puede seguir editándose.

## Archivos principales y extensión

- Backend: `common/access/{ProductFields,FieldAuthorization,RbacMigration}`;
  `productos/{dto/ProductoDtos,service/ProductoService,web/ProductPayloads}` y
  sus controladores; `common/storage/ProductImageStorage`.
- Frontend: `core/services/{product-fields,product.service,company-permissions.service}`;
  `shared/components/product-field-editor`; catálogo, detalle y edición de productos;
  `users/rols/{permission-matrix,rols}` y ajustes de personal.
- Pruebas: `ProductFieldRbacTests`, fixtures de migración y tests Angular de matriz,
  payloads, editor, navegación y actualización del contexto.

Para agregar otro recurso, registrar sus permisos y metadatos en una nueva versión
de migración, aplicar `FieldAuthorization` antes de escribir/proyectar y consultar
las mismas capacidades en su formulario. La matriz incorpora los metadatos nuevos
sin añadir una lista de IDs en el frontend. Los tipos y validaciones comerciales
siguen perteneciendo al formulario/servicio del recurso.

## Límites de esta entrega

El piloto cubre los endpoints de productos y su CSV. La cobertura de ventas,
cajas, inventario, reportes, comprobantes y otros canales corresponde a fases 6–7.
Las URL de archivos bajo `/media` conservan el acceso existente: omitir una
referencia de imagen no revoca una URL conocida. Los archivos públicos, imágenes
históricas y canales alternativos requieren la revisión de fase 7.

La validación de producción sobre PostgreSQL, concurrencia y retirada definitiva
de compatibilidad continúa en fase 8. Esta implementación no atribuye al piloto
una protección que todavía no se haya aplicado a los otros módulos.

## Verificación realizada

- Suite completa de backend: **49 pruebas aprobadas**, incluidas 12 de productos.
  La comprobación posterior de JSON serializado distingue claves ausentes de
  claves autorizadas con valor `null`.
- Frontend: **46 pruebas aprobadas**, con matriz dinámica, dependencias sin
  concesiones automáticas, payloads parciales, controles sin lectura, revocación,
  delegación, navegación y guardado de roles.
- Compilación de producción correcta. Se mantiene el aviso de tamaño inicial:
  aproximadamente 1,48 MB frente al umbral de aviso existente de 600 kB.
- Navegador local sobre H2 temporal: crear un rol con siete permisos, volver a
  abrirlo y comprobar la selección; consultar y editar un producto, guardar solo
  el nombre y conservar precio y costo. La captura de pantalla del navegador no
  estuvo disponible por un fallo de CDP; la comprobación funcional utilizó los
  controles y el árbol de accesibilidad.

Comandos: `./mvnw.cmd package` desde backend; `npm test -- --watch=false` y
`npm run build` desde frontend. La revisión no utiliza cuentas ni bases de datos
reales para poblar o modificar datos.
