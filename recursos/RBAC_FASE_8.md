# RBAC — fase 8: validación, migración y operación

Entrega final del plan RBAC, 27 de octubre de 2026. Esta documentación se refiere
a los módulos existentes del POS. El contrato de campos usa sus nombres reales:
`precio`, `costo`, `nombre`, etc.; no `sale_price` ni `purchase_price`.

## Cambios del cierre

- Los consumidores de Angular y los servicios de negocio usan códigos canónicos
  `recurso:acción[:campo]`. Un alias como `PRODUCTO_VER` ya no autoriza una acción.
  Los IDs históricos y `permiso.codigo` se conservan para no romper relaciones;
  `codigo_canonico` es la autoridad. El mapa histórico se usa exclusivamente al
  convertir datos antiguos y registrar el catálogo inicial.
- Se retiraron `RoleFields` y `PrivilegioCampoService`. La tabla antigua
  `privilegio_campo` se conserva solo como entrada de la migración V1, para poder
  actualizar instalaciones antiguas. No se lee para decidir permisos actuales.
- El editor solo envía `permiso_ids` y `delegable_ids`. El atributo de solicitud
  `campos` se rechaza con 400. Los antiguos endpoints `mis-campos` y `/{id}/campos`
  responden 410, sin leer ni modificar concesiones. Actualizar ambos servicios
  juntos evita que una pestaña con una versión anterior siga usando ese formato.
- Después de adquirir el bloqueo de empresa se descartan entidades cargadas antes
  de esperar: se vuelven a leer el rol y las capacidades del actor. Esto evita
  sobrescribir metadatos confirmados y usar delegaciones retiradas mientras otro
  editor esperaba. Las capacidades administrativas compartidas usan ámbito de
  empresa; una presencia global sin permisos no amplía concesiones de sucursal.
  Si dos solicitudes escriben explícitamente el mismo campo, prevalece la que se
  confirma después del bloqueo: no se introduce una fusión automática de formularios
  ni un control de versión de la edición en el navegador.
- La migración toma un bloqueo transaccional de PostgreSQL para serializar su
  conversión entre instancias. No se introduce un cuarto marcador: V1–V3 siguen
  identificando las conversiones de datos existentes.
- `RequestReadCache` reutiliza asignaciones y concesiones únicamente dentro de
  un GET. No sobrevive a la solicitud ni se usa para escrituras o WebSockets.
  Cien resoluciones con los campos de producto requieren dos consultas de
  autorización, igual que una; esto no es una medición de todas las consultas del
  endpoint ni una prueba de carga del sistema completo.

## Sesiones abiertas y operaciones en curso

El JWT identifica al usuario, pero no contiene concesiones autoritativas. Cada
solicitud vuelve a validar usuario activo, pertenencia, rol, concesión, catálogo,
empresa, plan y ámbito aplicables. Conceder o retirar permisos no exige volver a
iniciar sesión ni emitir otro JWT.

La interfaz refresca el contexto cada 30 segundos y al recuperar el foco. Los
WebSockets del dashboard validan antes de cada envío y cada cinco segundos aun
sin mensajes del cliente; al perder autorización cierran con 1008. Las pruebas
comprueban el cierre por el temporizador real, sin enviar otro mensaje del cliente.

`PolicyRevisionService` cambia su revisión al cambiar concesiones, empresa,
sucursal, suscripción o sesión. El interceptor descarta una respuesta de datos o
archivo iniciada con una revisión anterior, y los reportes descartan sus snapshots.
Un refresco con las mismas concesiones no invalida una lectura correcta.
Las respuestas que establecen el contexto y la autenticación quedan fuera de ese
filtro. Un descarte local usa 409; una escritura puede haberse confirmado en el
servidor y **no se reintenta automáticamente**.

La revocación se aplica a la siguiente operación autorizada por el servidor. La
pantalla puede tardar hasta el siguiente refresco en enterarse. No se promete
cancelar transacciones ya autorizadas ni borrar archivos que alguien descargó
legítimamente antes de la revocación.

## Matriz de aceptación reproducible

| Criterio | Evidencia automatizada |
|---|---|
| Crear un rol y seleccionar módulos/acciones | `rols.spec.ts`, `permission-matrix.spec.ts`, `RbacTests` |
| Elegir lectura/alta/edición por atributo | `permission-matrix.spec.ts`, `product-field-editor.spec.ts`, `ProductFieldRbacTests` |
| Guardar de forma atómica y asignar a personal | `RbacTests`, `PosOperationsRbacTests`; rollback ante permiso inválido e invitaciones revocadas |
| Interfaz y rutas conforme al contexto | `auth.guard.spec.ts`, `company-permissions.service.spec.ts`, pruebas de productos y ventas |
| HTTP manipulado y aislamiento entre empresas/sucursales | `ProductFieldRbacTests`, `PosOperationsRbacTests`, `ReadChannelsRbacTests` |
| Concesión/revocación con el mismo JWT | `RbacFinalValidationTests`, `ReadChannelsRbacTests` |
| Respuestas pendientes después de revocar o cambiar empresa | `policy-revision.interceptor.spec.ts`, `read-channel.spec.ts`, `secure-src.spec.ts` |
| Último propietario y ediciones simultáneas | `RbacFinalValidationTests`: dos hilos, bloqueo real, delegación revocada durante la espera |
| Migración idempotente y derechos conservados | `RbacLegacyMigrationTests` en H2 y PostgreSQL; duplicados restrictivos y reinicio sin restaurar concesiones |
| Catálogo extensible y canales alternativos | Catálogo/metadatos, matriz generada y pruebas QBE/analítica/archivos/notificaciones/WebSocket |

Incluye identidad de plataforma, propietario, gestor personalizado, cajero y rol
sin concesiones. El superadministrador puede administrar la plataforma; su JWT o
nombre de rol no concede pertenencia implícita a una empresa. El propietario no
puede ser sustituido/desactivado desde personal ni editando la plantilla protegida.
No se limita esta protección al caso de un solo propietario.

## Ensayo de PostgreSQL, respaldo y recuperación

Ejecutar desde la raíz, con PostgreSQL 17 en PATH y Java/Node instalados:

```powershell
./scripts/Test-RbacPostgres.ps1
```

El script crea una instancia nueva en loopback, puerto 55439, con el usuario
ficticio `rbac_test`. No lee las credenciales del POS ni acepta una dirección de
base externa. Los datos viven en `pos-backend/target/rbac8-drill-<id>`.

1. Prepara un esquema antiguo mínimo y ejecuta dos veces los scripts V001 y V003.
2. Ejecuta las siete suites RBAC contra bases separadas de PostgreSQL real.
3. Conserva el fixture de aceptación final y realiza `pg_dump -Fc`.
4. Restaura con `pg_restore --exit-on-error` en otra base vacía.
5. Compara una huella de roles, permisos, concesiones activas/inactivas,
   delegaciones, asignaciones y marcadores V1–V3.
6. Guarda logs, resultados JUnit, dump y `result.json`; detiene su instancia en
   `finally`. No elimina bases o servicios PostgreSQL ya existentes.

`RBAC_TEST_JDBC_URL` es una opción de pruebas con validación estricta: solo admite
loopback, puertos 55430–55439 y el prefijo `rbac8_`. Sin ella las suites usan H2.
No usarla para señalar una base con información real: los fixtures crean su esquema.

## Actualizar una instalación existente

1. Detener las escrituras y guardar un respaldo PostgreSQL completo fuera de la
   carpeta de compilación, junto con medios/XML y la versión previa del código.
2. Restaurar ese respaldo en una copia y comprobar restauración antes de actualizar.
3. Si no se usa `ddl-auto:update`, ejecutar los scripts PostgreSQL
   `db/rbac/postgresql/V001__schema.sql` y `V003__client_scope.sql` antes del arranque.
   No insertar manualmente marcadores en `rbac_migration`.
4. Iniciar el backend actualizado: `CatalogSeedRunner` llama a `RbacMigration`.
   V1 convierte roles/concesiones/campos antiguos; V2 registra los demás campos
   de producto; V3 acota clientes y registra acciones comerciales.
5. Ejecutar `db/rbac/postgresql/verify_rbac.sql`. Revisar IDs sin mapeo, clientes
   sin empresa, asignaciones cruzadas y empresas sin propietario activo.
   Los clientes históricos ambiguos permanecen sin ámbito y no se publican en
   consultas por empresa. Corregir su asociación usando evidencia del negocio;
   nunca elegir una empresa arbitraria ni dar permisos para ocultar un diagnóstico.
6. Reiniciar frontend y backend, probar propietario/cajero y un usuario limitado,
   y comprobar el flujo de planes/pagos. Conservar el respaldo previo hasta aceptar
   la actualización. Los seeds no deben reponer concesiones retiradas.
7. Ante una falla de conversión, no borrar el marcador ni reabrir permisos.
   Revertir a la versión anterior y restaurar la copia completa validada. En
   PostgreSQL la conversión es transaccional; el DDL de H2 no tiene ese mismo contrato.

El ensayo incluido usa datos ficticios. La actualización de una base real sigue
requiriendo revisar sus diagnósticos particulares; esta entrega no ejecutó esa
actualización sobre las cuentas del usuario.

## Endpoints y puntos de extensión

| Operación | Ruta/servicio |
|---|---|
| Contexto efectivo y suscripción | `GET /api/empresas/{empresa}/mis-permisos?id_sucursal=...` |
| Catálogo para la matriz | API de módulos/permisos, `ModuloPermisoService` |
| Roles visibles/capacidades/asignables/delegables | `GET /api/roles/empresa/{empresa}` y sus sufijos |
| Alta atómica | `POST /api/roles/empresa/{empresa}` |
| Consulta/edición atómica | `GET/PUT /api/roles/{rol}` |
| Asignación/invitación de personal | Operaciones de `SucursalService`, contrato de la fase 6 |
| Productos, caja, ventas, inventario, clientes y pedidos | Matriz de [fase 6](RBAC_FASE_6.md) |
| Reportes/QBE/exportación, archivos y canales en vivo | Contratos de [fase 7](RBAC_FASE_7.md) |

Para incorporar un recurso:

1. Registrar sus códigos canónicos y metadatos (`recurso`, `accion`, `atributo`,
   descripción, módulo, estado) en una nueva conversión versionada. No concederlos
   a todos los roles al reiniciar; cualquier concesión inicial debe ser explícita.
2. Definir empresa/sucursal y si el recurso es compartido. Usar `EmpresaAccess` y
   `RbacResolver`; para operaciones globales, `resolveCompany`.
3. Validar todos los campos suministrados con `FieldAuthorization.require` antes
   de modificar la entidad. Usar `project` para omitir atributos sin lectura del
   JSON; no sustituirlos por valores ficticios ni `null`.
4. Registrar dependencias de columnas, filtros, agregados y exportaciones en
   `DataReadPolicy` y aplicar la política también a los archivos/canales auxiliares.
5. La matriz Angular genera acciones y campos desde el catálogo. Usar
   `hasPermission`, `hasField` y `data.permission` para rutas; una lista de permisos
   en el guard es una alternativa OR, no una condición AND.
6. Agregar pruebas permitidas/denegadas, aislamiento, campos omitidos, payload
   manipulado y revocación. Las escrituras de roles/asignaciones deben tomar el
   bloqueo al principio, antes de cambiar entidades, y revalidar después de esperar.

## Evidencia de ejecución y alcance

Resultados ampliados con la [corrección de catálogos existentes](RBAC_CORRECCION_CATALOGO.md),
sin fallos ni pruebas omitidas. La validación Angular conserva su ejecución anterior;
esta corrección modifica únicamente backend y el ensayo de PostgreSQL:

| Verificación | Resultado |
|---|---|
| Backend completo en H2 y empaquetado Java | **112 pruebas aprobadas** |
| Misma matriz RBAC en PostgreSQL 17.4 | **93 pruebas aprobadas** en siete suites |
| Frontend Angular | **63 pruebas aprobadas** en 18 archivos |
| Compilación de producción Angular | Correcta; conserva el aviso existente de 1,49 MB frente a 600 kB |
| Scripts sobre esquema antiguo | V001 y V003 ejecutados dos veces sin error |
| Respaldo y recuperación PostgreSQL | Huellas de autorización idénticas; marcadores 1, 2, 3 |
| Diagnóstico de la copia restaurada | Cero incidencias en las cinco consultas de integridad |
| Artefacto Java | Sin las clases retiradas `RoleFields`/`PrivilegioCampoService`; incluye la política y el diagnóstico |

Evidencia local (temporal e ignorada por Git):

- `pos-backend/target/rbac-catalog-package.log` y `target/rbac-catalog-h2-reports`.
- `pos-backend/target/rbac8-drill-3bc552c9afff426fa5feb8694e410d7d/`:
  `tests.log`, siete reportes JUnit, `migrated-fixture.dump`, `result.json`,
  `diagnostics.txt` y logs de la instancia detenida.
- `pos-frontend/.angular/rbac8-tests.log` y `rbac8-build.log`.

Comandos reproducibles:

```powershell
# Desde pos-backend, sin RBAC_TEST_JDBC_URL: H2 + JAR.
./mvnw.cmd package
# Desde pos-frontend.
npm.cmd test -- --watch=false
npm.cmd run build
# Desde la raíz: PostgreSQL aislado + respaldo/restauración.
./scripts/Test-RbacPostgres.ps1
```

Las instancias temporales quedaron detenidas. No se modificó la base habitual ni
se enviaron correos reales. La comprobación de Angular usa sus componentes,
servicios, guards e interceptor con HTTP controlado; esta fase no realizó una
nueva sesión manual de navegador contra cuentas reales.

## Archivos de esta fase

- Backend: `common/access/RequestReadCache.java`, `RbacResolver.java`,
  `RoleAdministrationPolicy.java`, `RbacMigration.java`, `PermissionCodes.java`,
  `EmpresaAccess.java`, `usuarios/service/RolService.java`,
  `usuarios/web/RolController.java` y llamadas de negocio convertidas a códigos
  canónicos. Se retiran los dos servicios antiguos indicados arriba.
- Frontend: `policy-revision.service.ts`, `policy-revision.interceptor.ts`,
  `watch-read-permissions.ts`, `company-permissions.service.ts`, `app.config.ts`
  y consumidores de permisos en rutas, navegación y pantallas del POS.
- Pruebas: `RbacFinalValidationTests.java`, `support/RbacTestDatabase.java`,
  la opción PostgreSQL de las suites RBAC existentes, la migración histórica,
  el temporizador WebSocket y `policy-revision.interceptor.spec.ts`.
- Operación: `scripts/Test-RbacPostgres.ps1`,
  `db/rbac/postgresql/verify_rbac.sql`, este documento y `PLAN_RBAC.md`.

Las ocho fases del plan quedan completadas para los módulos existentes. La
migración de una instalación real, con revisión de datos ambiguos y su respaldo,
es una operación de despliegue descrita arriba; no fue ejecutada sobre tus datos.

Esta fase no crea módulos comerciales nuevos ni elimina la documentación histórica.
Los informes de fases anteriores describen la entrega de su momento; este documento
prevalece respecto a los adaptadores retirados y el contrato de revocación actual.
