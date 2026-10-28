# Plan de implementación de RBAC granular

Estado: fases 1–8 completadas. Validación final: 27 de octubre de 2026.
Base analizada el 26 de octubre de 2026.
Requerimientos: [prompt_RBAC.MD](prompt_RBAC.MD).

La entrega de las fases 1–3 incluye el resolver central, administración segura de
roles, migración y pruebas. El contrato, las decisiones y los límites de esta
implementación se describen en [RBAC_FASES_1_3.md](RBAC_FASES_1_3.md).
Los apartados siguientes conservan la planificación original y sus criterios de salida.

La entrega de las fases 4–5 completa el piloto de productos y la matriz visual.
Su contrato, migración V2 y pruebas se describen en
[RBAC_FASES_4_5.md](RBAC_FASES_4_5.md).

La fase 6 integra las operaciones comerciales por acción y sucursal. Su contrato,
permisos nuevos y migración V3 se describen en [RBAC_FASE_6.md](RBAC_FASE_6.md).

La fase 7 protege reportes, archivos, notificaciones y canales en vivo. Su contrato,
restricciones de ámbito y pruebas se describen en [RBAC_FASE_7.md](RBAC_FASE_7.md).

La fase 8 completa la validación en H2/PostgreSQL, revocación, concurrencia,
respaldo/recuperación y retirada de adaptadores. Resultados actualizados: 112 pruebas de backend
completo, 93 de la matriz RBAC en PostgreSQL y 63 de frontend, todas aprobadas.
Procedimiento operativo y límites: [RBAC_FASE_8.md](RBAC_FASE_8.md).
La base habitual del usuario no se modificó.
La corrección del arranque con catálogos existentes y sus regresiones está en
[RBAC_CORRECCION_CATALOGO.md](RBAC_CORRECCION_CATALOGO.md).

## 1. Base existente y hallazgos

Stack actual: Angular 21, TypeScript, Tailwind y Lucide; Spring Boot 4.1.1,
Java 17, Spring Security, JWT y JPA. PostgreSQL como base principal y H2 para
desarrollo/pruebas. Hay seeds y `ddl-auto: update`; no se identificó un gestor
de migraciones versionadas en las dependencias inspeccionadas.

Se reutilizarán `usuario`, `rol`, `permiso`, `rol_permiso`, `usuario_rol`,
`privilegio_campo`, los servicios actuales y la pantalla de roles. La relación
usuario–rol ya admite varias asignaciones por empresa y sucursal.

| Hallazgo por lectura del código | Implicación | Evidencia |
|---|---|---|
| Los métodos generales de roles listan, consultan y modifican por ID sin resolver el usuario ni comprobar su empresa o permisos. La seguridad HTTP general exige autenticación. | Priorizar autorización de estas operaciones y protección administrativa. | `RolController`, `RolService`, `SecurityConfig` |
| `EmpresaAccess.requirePermiso` permite continuar si el código no está registrado. | Un permiso ausente o mal escrito puede permitir la operación. | `common/access/EmpresaAccess.java` |
| `misPermisos` y los estados de campos usan `roles.get(0)`. | El resultado puede omitir otras asignaciones; no hay una resolución efectiva uniforme. | `EmpresaService`, `PrivilegioCampoService` |
| No todas las consultas de autorización filtran explícitamente `rol.activo`. | Revisar la revocación al desactivar un rol, además del estado de la relación. | `EmpresaAccess`, `UsuarioRolRepository` |
| Los privilegios de producto cubren `costo`, `precio`, `editar` y `eliminar`; el valor de respaldo es `EDITABLE`. | Se mezclan campos con acciones, y lectura/creación/actualización no son independientes. | `PrivilegioCampoService` |
| Se ignoran determinados cambios no autorizados en precio/costo y se asigna `null` a datos ocultos en el DTO. | El contrato requerido debe rechazar escrituras prohibidas y omitir las claves protegidas del JSON. | `ProductoService.actualizarProducto`, `toProducto` |
| El frontend habilita todos los permisos si no encuentra datos guardados o si todos son falsos; el catálogo de productos también tiene una excepción para permisos vacíos. | La interfaz puede mostrar acciones que deberían quedar bloqueadas. | `CompanyPermissionsService.readStoredPermissions`, `ProductosPanel.hasPermission` |
| El formulario de roles guarda permisos generales y privilegios de campos en dos solicitudes. | Puede quedar una configuración parcial si falla la segunda petición. | `rols.ts`, `RolController` |
| Los DTO de roles no incluyen toda la información visible del formulario. | Incorporar nombre, descripción y estado con autorización explícita por campo. | `RolCreateRequest`, `RolUpdateRequest`, `RolService` |
| Ventas asigna importes, descuentos y precios desde el payload. | Revisar el flujo completo y asegurar cálculos y validaciones en el servidor. | `VentaService` |

Estos hallazgos son una revisión estática, no una prueba de explotación ni una
auditoría completa de todos los endpoints.

Rutas de referencia:

- Backend: `pos-backend/src/main/java/com/pos/{usuarios,common/access,empresas,productos,ventas}`.
- Frontend: `pos-frontend/src/app/core/{services,guards}` y
  `features/companies/sections/users/rols`.
- Catálogo inicial: `pos-backend/src/main/java/com/pos/common/seed/CatalogSeedRunner.java`.

## 2. Reglas propuestas para todas las fases

1. **El backend decide.** La UI representa la decisión y todos los endpoints
   implicados deben aplicarla, incluso ante solicitudes HTTP manuales.
2. **Denegación por defecto.** Un permiso inexistente, inactivo o no concedido
   bloquea la operación. La carga pendiente de permisos no habilita controles.
3. **Contexto antes de agregar permisos.** Filtrar las asignaciones activas por
   usuario, empresa y sucursal; después unir sus concesiones. Una asignación de
   otra sucursal no concede acceso fuera de su ámbito. La semántica de las
   asignaciones de empresa y sucursal se debe concretar en la fase 2.
4. **Recurso y atributo se comprueban juntos.** Los campos protegidos requieren
   el permiso general y el permiso del atributo para esa acción. Los campos
   no protegidos heredan el permiso general. Lectura y escritura son independientes.
5. **Una sola fuente de concesiones.** Migrar los estados actuales de
   `privilegio_campo` al catálogo canónico; mantener un adaptador temporal, con
   fecha de retirada. Evitar dos motores de autorización definitivos.
6. **Planes y permisos se combinan.** El acceso efectivo considera sesión,
   empresa/sucursal, función habilitada por el plan y permisos del rol. Conservar
   el flujo de Stripe y los límites de los planes.
7. **Cambios atómicos.** Una solicitud con un campo protegido no autorizado
   devuelve 403 y no aplica cambios parciales. Distinguir campo ausente, valor
   explícito `null` y valor nuevo, especialmente en actualizaciones.
8. **Sin privilegios por nombres o UI.** El superadministrador de plataforma
   conserva su identidad protegida. Los roles personalizados y la autorización
   de pagos/propiedad de empresa deben tener reglas explícitas.
9. **Pruebas en cada fase.** Cada entrega incluye casos permitidos y denegados,
   aislamiento entre empresas/sucursales y regresión de los flujos afectados.

Convención propuesta: `recurso:accion` y `recurso:accion:campo`, conservando
los nombres reales de atributos del backend, por ejemplo:

| Actual | Canónico propuesto |
|---|---|
| `PRODUCTO_VER` | `product:read` |
| `PRODUCTO_CREAR` | `product:create` |
| `PRODUCTO_EDITAR` | `product:update` |
| Visibilidad de `costo` | `product:read:costo` |
| Escritura de `precio` | Separar `product:create:precio` y `product:update:precio` |
| `ROL_EDITAR` | `role:update`, complementado por `role:update:permissions`, `role:update:nombre`, etc. |
| `CAJA_CERRAR` | `cash_register:close` |

Los códigos anteriores requieren un mapeo explícito. Se conservan los IDs y las
relaciones siempre que sea viable. El ejemplo `product:update:stock` del prompt
se adaptará al dominio real: las existencias se modifican por operaciones de
inventario, con sus permisos y movimientos; no mediante edición directa del producto.

## 3. Fases y criterios de salida

### Fase 1 — Contención y contrato de autorización

**Objetivo:** cerrar los permisos implícitos y proteger la administración actual.

- Inventariar endpoint, recurso, acción, ámbito, campos y permiso existente.
- Proteger consulta, creación, edición, asignación y privilegios de roles con
  comprobaciones de usuario, empresa y permisos del catálogo vigente.
- Bloquear la modificación de roles globales/protegidos desde una empresa y
  conservar al menos un administrador autorizado por negocio.
- Corregir los respaldos permisivos del backend y frontend, después de verificar
  que los códigos utilizados estén registrados y que el administrador conserve acceso.
- Diferenciar sesión sin autenticar, permiso insuficiente y recurso ajeno.
- Documentar la matriz inicial de autorización y los errores de API.

**Salida:** un usuario autenticado de la empresa A no puede administrar roles de B;
un usuario sin `ROL_EDITAR` no puede cambiar permisos mediante la API; un catálogo
vacío no habilita acciones. Las pruebas de regresión de registro y planes siguen pasando.

### Fase 2 — Catálogo, modelo de datos y resolución central

**Objetivo:** establecer el motor compartido que usarán los módulos.

- Extender el catálogo con acción, atributo opcional, descripción, estado y
  metadatos necesarios para generar formularios. Diferenciar catálogo y concesión.
- Sincronizar códigos canónicos y aliases controlados para la transición.
- Definir y probar los ámbitos empresa/sucursal, incluidos administradores con
  varias sucursales y recursos compartidos como el catálogo de productos.
- Resolver la unión de permisos activos de todas las asignaciones aplicables,
  filtrando usuario, relación, rol y permiso; unificar `mis-permisos` con ese resultado.
- Agregar restricciones de unicidad, claves foráneas e índices. Prever nombres de
  roles repetidos entre empresas y plantillas globales con empresa nula.
- Preparar scripts versionados y seeds idempotentes para PostgreSQL y fixtures H2;
  ensayar la conversión de datos existentes sin ampliar concesiones automáticamente.
- Mantener la resolución autorizativa en servidor por solicitud; definir la
  actualización del contexto del frontend al cambiar roles, empresa o sucursal.
  Cualquier caché futura deberá tener invalidación/versionado explícito.

**Salida:** dos roles válidos se combinan de forma determinista en el mismo ámbito;
desactivar un rol o permiso revoca acceso en la siguiente solicitud; la migración
se puede repetir sin duplicar permisos ni restaurar concesiones retiradas.

### Fase 3 — Administración segura y atómica de roles

**Objetivo:** completar la API administrativa sobre el motor de la fase 2.

- Permitir nombre, descripción, estado y permisos en una operación transaccional.
- Consultar roles activos/inactivos y administrar asignaciones por empresa/sucursal.
- Separar permisos que el actor posee de los que está autorizado a conceder.
- Proteger creación y actualización de cada atributo del rol; validar IDs contra
  el catálogo activo y el conjunto administrable del actor.
- Prohibir escalamiento por edición del propio rol o cambios indirectos de
  `usuario_rol`, incluyendo modificaciones de rol y sucursal del personal.
- Aplicar reglas de roles protegidos, último administrador, nombres reservados,
  desactivación y dependencias. Preferir desactivación ante referencias existentes.
- Registrar cambios sensibles sin incluir secretos ni valores ocultos en la bitácora.

**Salida:** manipular el payload no concede privilegios superiores; una falla al
guardar permisos revierte todo el cambio; nombre, descripción y estado se guardan
correctamente conforme a las capacidades del actor.

### Fase 4 — Piloto completo en productos

**Objetivo:** demostrar las reglas granulares en un módulo existente de extremo a extremo.

- Registrar los campos reales: nombre, descripción, precio, costo, unidad de medida,
  subcategoría, código de barras, imagen y estado, según la matriz de protección.
- Aplicar permisos generales en lectura, creación, actualización y eliminación.
- Separar permisos de lectura, creación y actualización de atributos protegidos.
- Validar los campos enviados antes de cambiar la entidad, incluyendo `null`,
  actualizaciones parciales, cambios de imagen y altas con campos obligatorios.
- Omitir del JSON los atributos prohibidos en listados y detalles, y excluirlos
  también del CSV del catálogo. Probar el JSON serializado, no solo el DTO Java.
- Actualizar lista y formularios para enviar únicamente campos editables y mostrar
  valores solo cuando exista lectura autorizada.
- Migrar los estados anteriores: `EDITABLE`, `SOLO_LECTURA`, `OCULTO`; convertir
  `editar`/`eliminar` a reglas de acciones y resolver casos ambiguos explícitamente.

**Salida demostrable:** un cajero cambia el nombre y la descripción del producto,
no recibe `costo` en el JSON/CSV y obtiene 403 al enviar manualmente un precio
protegido. El usuario que sí posee los permisos puede realizar las operaciones.

### Fase 5 — Editor visual de roles y componentes reutilizables

**Objetivo:** administrar la política desde Angular con el catálogo real del backend.

- Reutilizar y adaptar la pantalla actual: listado, búsqueda, estado y acciones autorizadas.
- Formulario con nombre, descripción, estado y módulos ordenados.
- Separar acciones generales y matriz de atributos con columnas Ver/Crear/Editar.
- Mostrar guion o celda deshabilitada cuando la acción no exista en el catálogo.
- Agregar contador, búsqueda de permisos, selección individual y estados de carga/error.
- Limitar las concesiones seleccionables a las capacidades administrables del actor;
  el backend vuelve a validar todo al guardar.
- Crear utilidades/directivas/guards de permisos para campos, acciones y rutas.
  Eliminar las excepciones locales que habilitan acceso por falta de configuración.
- Representar las dependencias con validación o avisos; seleccionar lectura no debe
  conceder escritura ni seleccionar un atributo conceder acceso general silenciosamente.

**Salida:** se crea un rol personalizado, se asigna a un usuario y su sesión refleja
la misma política en interfaz y API. Cambiar la política actualiza las sesiones
afectadas sin tener que borrar manualmente el almacenamiento del navegador.

### Fase 6 — Operaciones comerciales del POS

**Objetivo:** extender el patrón validado por entregas pequeñas.

Orden sugerido de subentregas: **6A ventas → 6B cajas → 6C inventario →
6D usuarios/clientes/pedidos y configuración de empresa**.

- Controlar lectura, creación, cambios de precio y descuentos en ventas.
- Recalcular y validar importes, impuestos, totales y coherencia de movimientos
  en backend; el permiso de escritura solo habilita entradas de negocio admitidas.
- Autorizar apertura/cierre y movimientos de caja con acciones específicas.
- Aplicar permisos a ajustes y movimientos de inventario por sucursal.
- Revisar asignaciones, invitaciones y cambios del personal, así como las
  comprobaciones actuales basadas en nombres de rol, incluidas las de pagos.
- Integrar anulación, devolución u otras acciones únicamente donde ya existan
  operaciones de negocio; no construir módulos nuevos solo para demostrar permisos.

**Salida por subentrega:** cada operación tiene pruebas HTTP permitidas/denegadas,
aislamiento de sucursal y validación de cálculos. Manipular el frontend no permite
modificar valores derivados ni ejecutar operaciones fuera del ámbito autorizado.

### Fase 7 — Reportes, exportaciones y otros canales de datos

**Objetivo:** cerrar caminos alternativos de lectura y modificación.

- Aplicar la misma política en reportes estándar/dinámicos/QBE, dashboard,
  analítica, comprobantes y exportaciones CSV/Excel/PDF que existan.
- Revisar cantidades agregadas o derivadas que permitan inferir un dato protegido,
  por ejemplo ganancias que revelen el costo de compra.
- Inventariar websockets, notificaciones, respuestas de error, archivos XML,
  medios y descargas para clasificar cuáles necesitan autorización.
- Verificar servicios auxiliares de analítica si están activos en el flujo real.
- Agregar un punto de extensión para aplicar permisos antes de serializar o
  exportar, evitando filtros distintos para cada vista.

**Salida:** un costo restringido permanece fuera de los canales aplicables y no
puede recuperarse usando otro endpoint, formato, empresa o sucursal.

### Fase 8 — Cierre, migración y documentación operativa

**Objetivo:** completar la cobertura y retirar la compatibilidad temporal.

- Ejecutar la matriz integral con superadministrador, administrador de negocio,
  rol personalizado, cajero y usuario sin permisos; incluir varias empresas/sucursales.
- Probar concesión y revocación en sesiones abiertas, concurrencia al modificar
  roles y protección del último administrador.
- Ensayar respaldo, migración y recuperación en una copia; comprobar que los
  roles existentes mantienen las concesiones previstas y ninguna se amplía por accidente.
- Verificar PostgreSQL además de H2, y el rendimiento del resolver para evitar
  consultas repetidas por campo o por fila de una lista.
- Retirar aliases/adaptadores solo después de migrar todas sus llamadas y datos.
- Documentar endpoints, archivos, scripts, alta de un módulo, pruebas y pendientes.

**Salida:** se satisfacen los diez criterios de aceptación del prompt con evidencia
reproducible y no quedan rutas de autorización antiguas activas fuera del contrato.

## 4. Entregas y forma de trabajo

- **Entrega A — Base segura:** fases 1–3. API y modelo preparados para la granularidad.
- **Entrega B — Primera demo completa:** fases 4–5. Rol personalizado y producto con
  lectura/escritura por campo, UI funcional y rechazo real de solicitudes prohibidas.
- **Entrega C — Cobertura del POS:** fases 6–7, por módulos y canales existentes.
- **Entrega D — Cierre:** fase 8, con migración y criterios de aceptación completos.

Cada fase debe terminar en un commit o PR revisable con objetivo, cambios,
migraciones y pruebas ejecutadas. La fase 6 se divide en varias entregas para
evitar un cambio masivo de todos los módulos. Ninguna fase posterior sustituye
las pruebas de seguridad y regresión de la fase que se está desarrollando.

Las secciones anteriores conservan la planificación original. Para operar la
versión final y preparar una actualización real, usar [RBAC_FASE_8.md](RBAC_FASE_8.md).
