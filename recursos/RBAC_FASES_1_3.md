# RBAC: contrato de las fases 1–3

Continuación implementada: [productos y matriz visual, fases 4–5](RBAC_FASES_4_5.md).

Se conserva el stack y las tablas existentes. Los códigos anteriores conservan sus
IDs y `codigo`; `codigo_canonico` es la autoridad común. El catálogo también entrega
recurso, acción, atributo opcional, descripción y estado. El mapeo explícito vive en
`PermissionCodes`; no se deducen permisos de etiquetas o nombres de roles personalizados.

## Ámbito y resolución

- Solo participan usuario, asignación, rol, concesión y permiso activos. El rol debe
  pertenecer a la empresa o ser una plantilla global del sistema. Las asignaciones
  con una sucursal de otra empresa no participan.
- Una asignación con sucursal nula tiene ámbito de empresa. Una asignación concreta
  concede permisos en esa sucursal. Se unen todos los roles aplicables.
- Los recursos compartidos de empresa, como catálogo y roles, usan el contexto de
  empresa. Las operaciones de una sucursal usan su identificador explícito. El
  resolver valida también que esa sucursal pertenezca a la empresa.
- El administrador propietario es la identidad global protegida `ADMINISTRADOR`,
  dentro de una relación con la empresa. Sus relaciones históricas con sucursales
  tienen alcance de empresa. Un rol personalizado nunca obtiene esta identidad
  por su nombre. El indicador `super_admin` de plataforma se mantiene separado y
  no constituye un bypass de los endpoints de empresa.
- Un código desconocido, sin concesión, inactivo o mal escrito deniega acceso.
  `RbacResolver.field` exige permiso general y del atributo para la misma acción.
- El servidor consulta la base en cada solicitud; los permisos del JWT no autorizan.
  Revocar un rol, permiso o relación afecta la siguiente solicitud con el mismo JWT.
  Los planes y sus límites se comprueban además de RBAC.

## Matriz administrativa

| Operación | Requisito |
|---|---|
| Listar roles activos e inactivos | Membresía y `role:read` |
| Leer metadatos de un rol de empresa | Empresa del rol y `role:read` |
| Recibir sus concesiones / campos | Además `role:read:permissions`; sin este permiso se omite `rol_permisos` |
| Crear rol | Ámbito de empresa, `role:create` y permisos de los atributos enviados |
| Modificar rol | Ámbito de empresa, `role:read`, `role:update` y permisos de los atributos enviados |
| Cambiar nombre / descripción / estado / concesiones | `role:create:campo` o `role:update:campo`, con `campo` igual a `nombre`, `descripcion`, `activo` o `permissions` |
| Conceder permiso o facultad de delegarlo | El actor debe poseerlo y tenerlo como delegable |
| Editar un rol existente | Sus concesiones activas también deben estar dentro del conjunto administrable del actor |
| Asignar personal / aceptar invitación | `role:assign`, `user:update`, ámbito de cada sucursal y rol administrable |
| Emitir invitación | Además `user:create`; se vuelve a validar al emisor al aceptar; enlace de un solo uso |
| Retirar o reemplazar al propietario | Denegado desde personal, incluso si hay otros propietarios |
| Editar plantilla global / identidad de plataforma | Denegado desde la administración de empresa |
| Crear sucursal con asignación implícita | `branch:create` y ámbito de empresa; un rol de sucursal no puede copiarse a otra |
| Leer o restaurar respaldo completo | Propietario; restaurar valida además capacidades y concesiones de roles |

Los nombres reservados no se admiten para roles personalizados. Los nombres son
únicos sin distinguir mayúsculas dentro de cada empresa, incluidas filas inactivas.
Se prefieren desactivaciones; desactivar no borra concesiones. Las concesiones
previamente revocadas siguen revocadas al habilitar el rol. Enviar
`permiso_ids: []` las retira explícitamente.

## API

Se conservan las rutas existentes. Se agregan:

- `GET /api/roles/empresa/{id}/delegables`: IDs que el actor puede conceder.
- `GET /api/roles/empresa/{id}/asignables`: roles activos que puede asignar.
- `GET /api/empresas/{id}/mis-permisos?id_sucursal=...`: contexto opcional de sucursal.

Las invitaciones nuevas usan `invitacion_rol`: se consumen una sola vez y cambiar
las asignaciones del personal revoca sus invitaciones pendientes. Los enlaces
anteriores sin este registro necesitan emitirse de nuevo.

Crear/editar acepta `nombre`, `descripcion`, `activo`, `permiso_ids`,
`delegable_ids` y, por compatibilidad, `campos` de producto. Son una sola transacción.
En actualizaciones, atributo ausente conserva su valor; `null` explícito o un
atributo desconocido devuelve 400. Una concesión prohibida devuelve 403 y revierte
toda la operación. Se usan 401 para sesión inválida, 404 para recursos ajenos/no
encontrados y 409 para un nombre de rol repetido.

`delegable_ids` debe ser subconjunto de `permiso_ids`. Omitirlo en una actualización
conserva únicamente las facultades anteriores que sigan concedidas. Poseer
`role:update` no permite inventar permisos ni modificar atributos sin autorización.
La pantalla actual incluye descripción y la opción “Puede conceder este permiso”,
usa un único guardado y no selecciona permisos dependientes automáticamente.

El frontend arranca sin permisos, consulta el contexto al navegar y al guardar
roles, lo limpia antes de cambiar empresa/sucursal y al salir. No usa permisos
guardados como autoridad. Una pantalla ya abierta puede conservar controles hasta
la próxima navegación/recarga; el servidor ya deniega la siguiente solicitud.

## Migración V1

Con el `ddl-auto: update` existente, JPA añade columnas y referencias de empresa y
sucursal. Al finalizar el catálogo inicial, `RbacMigration` convierte datos y crea
índices de unicidad para código canónico, nombre/ámbito, rol/permiso y asignación,
además del índice de consulta de contexto. Registra V1 en `rbac_migration`.

Para instalaciones que preparan el esquema fuera de Hibernate existen los scripts
`pos-backend/src/main/resources/db/rbac/{postgresql,h2}/V001__schema.sql`.
Son preparatorios; la conversión de datos versionada la hace el servicio Java.
No hay una dependencia nueva de Flyway/Liquibase ni ejecución automática de esos
scripts. No se debe marcar V1 manualmente ni eliminar su marcador para repetirla.

Conversión:

- Conserva IDs, códigos anteriores, asignaciones y concesiones revocadas.
- Convierte los estados históricos de costo/precio a permisos separados por acción.
  Los valores ausentes conservan la semántica anterior solo durante esta conversión;
  después el valor por defecto es denegado. La marca histórica de editar deshabilitado
  retira la edición general. Duplicados de concesiones se fusionan restrictivamente:
  si alguno estaba revocado, la concesión resultante queda revocada.
- Solo los propietarios reciben inicialmente facultad de delegar. Los administradores
  personalizados existentes deben recibir esa facultad explícitamente del propietario.
- Los roles de sistema se protegen. Los seeds conceden permisos predeterminados solo
  al crear por primera vez la plantilla; un reinicio no restaura concesiones retiradas.
- Nombres o asignaciones duplicados, o referencias inválidas, requieren corregir los
  datos antes de completar la migración; no se renombran ni reasignan silenciosamente.

Respaldar antes de migrar una base existente. PostgreSQL conserva transacciones de
DDL; H2 puede confirmar DDL implícitamente. La validación automatizada usa H2 y una
base con fixtures del modelo anterior. El ensayo en una copia de PostgreSQL sigue
siendo una comprobación de despliegue pendiente, sin modificar la base local de trabajo.
Nuevas ampliaciones de catálogo deben añadir una versión de migración explícita.

## Auditoría y alcance siguiente

Creación/edición de roles, invitaciones, asignaciones y restauración registran
eventos después de confirmar la transacción. Incluyen IDs, ámbito y tipo de cambio;
no contraseñas, tokens ni valores de campos protegidos. Una transacción rechazada
no registra un cambio exitoso.

`privilegio_campo` queda como fuente histórica de migración. El adaptador temporal
lee/escribe las concesiones canónicas; ya no hay un segundo motor que decida por
el primer rol. Se retirará al completar las fases 4–5 y migrar sus consumidores.

Esta entrega no completa el piloto de productos: omisión de todas las claves
protegidas, rechazo de sus escrituras, CSV y formularios corresponden a fase 4.
La matriz visual por atributo corresponde a fase 5. La cobertura completa de cada
operación comercial, reportes y otros canales sigue en las fases 6–8 del plan.

## Verificación

Desde `pos-backend`: `./mvnw.cmd test`.
Desde `pos-frontend`: `npm test -- --watch=false` y `npm run build`.

Los tests RBAC cubren HTTP con JWT, aislamiento, unión, ámbitos, revocación,
delegación, edición por atributo, rollback, propietario, invitaciones y rutas
indirectas. La suite existente cubre también registro, planes y Stripe de prueba.

Resultado verificado: **37 pruebas de backend y 30 de frontend aprobadas**.
La compilación de producción de Angular finaliza correctamente; mantiene el
aviso previo de tamaño inicial (aproximadamente 1,47 MB frente a 600 kB de aviso).
