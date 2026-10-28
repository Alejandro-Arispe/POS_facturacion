# Implementar RBAC con permisos granulares por módulo y atributo en el sistema POS

## 1. Objetivo general

Quiero implementar un sistema completo de **control de acceso basado en roles (RBAC), con autorización granular a nivel de campos o atributos (Field-Level Authorization)** en mi aplicación POS.

No quiero un sistema básico que solamente permita asignar roles como Administrador, Supervisor o Cajero. Necesito un sistema flexible donde pueda crear roles personalizados y decidir exactamente qué operaciones están autorizadas para cada rol, tanto a nivel de módulos como de campos individuales.

La implementación debe abarcar el frontend, backend y persistencia de datos, con responsabilidades claramente separadas.

**Importante:** Antes de implementar, analiza la arquitectura actual del proyecto, sus tecnologías, modelos, autenticación, estructura de carpetas y convenciones. Adapta la solución al proyecto existente. No introduzcas frameworks adicionales ni reestructures funcionalidades que ya funcionan sin una justificación técnica.

## 2. Sistema de roles

El sistema debe permitir administrar roles de manera dinámica.

Cada rol debe tener, como mínimo:

- Identificador único.
- Nombre.
- Descripción.
- Estado activo/inactivo.
- Conjunto de permisos asignados.
- Asociación con el negocio o tenant correspondiente, si aplica.

Debe ser posible crear, consultar, modificar y eliminar o desactivar roles, respetando las reglas de autorización y las restricciones de integridad.

Los usuarios podrán tener uno o varios roles, según lo permita la arquitectura del proyecto. Si el sistema actual solamente admite uno, no cambies esa relación sin necesidad.

Los permisos efectivos del usuario deben calcularse desde los roles que tenga asignados, considerando únicamente roles y permisos activos.

## 3. Estructura de los permisos

Los permisos deben manejarse mediante identificadores únicos y nombres estandarizados con la siguiente convención:

`modulo:accion`

Para permisos generales:

```text
product:read
product:create
product:update
product:delete

sale:read
sale:create
sale:update
sale:delete

role:read
role:create
role:update
role:delete
```

Para permisos granulares sobre atributos:

`modulo:accion:atributo`

Por ejemplo:

```text
product:read:purchase_price
product:read:sale_price
product:create:sale_price
product:update:sale_price
product:update:stock

sale:read:profit
sale:create:discount
sale:update:discount

role:read:permissions
role:create:permissions
role:update:name
role:update:description
role:update:is_active
role:update:permissions
```

Cada permiso debe tener una estructura lógica equivalente a:

```typescript
interface Permission {
  id: string;
  name: string;
  module: string;
  action: string;
  description: string;
  isActive: boolean;
}
```

Adapta los nombres de los atributos a los nombres reales de los campos del backend. La nomenclatura debe ser consistente entre frontend, backend y base de datos.

Los permisos disponibles deben provenir de un catálogo centralizado y administrado por el sistema, evitando permisos duplicados o identificadores inventados desde el frontend.

## 4. Reglas de autorización

Los permisos deben funcionar en dos niveles independientes y complementarios.

### Nivel A: Permisos generales del módulo

Determinan si un usuario puede realizar una operación sobre un recurso.

Ejemplo:

```text
product:read     → Consultar productos
product:create   → Crear productos
product:update   → Modificar productos
product:delete   → Eliminar productos
```

### Nivel B: Permisos granulares de atributos

Determinan cuáles campos específicos puede consultar, establecer o modificar.

Ejemplo: Un usuario podría tener:

```text
product:read
product:update
product:read:name
product:read:sale_price
product:update:name
```

En este caso:

- Puede consultar productos.
- Puede consultar su nombre y precio de venta.
- Puede modificar el nombre.
- No puede modificar el precio de venta si ese campo está protegido mediante un permiso granular que no posee.
- No puede consultar el precio de compra si está protegido mediante `product:read:purchase_price`.

### Reglas obligatorias

1. Los permisos generales habilitan la operación sobre el recurso, pero no deben anular las restricciones granulares.

2. Para consultar un atributo protegido, se debe tener permiso general de lectura y permiso granular de lectura para ese atributo.

3. Para modificar un atributo protegido, se debe tener permiso general de actualización y el permiso granular correspondiente.

4. Para establecer un atributo protegido durante la creación, se debe tener permiso general de creación y el permiso granular de creación.

5. Los campos que no tengan permisos granulares definidos pueden heredar los permisos generales del módulo. Los campos sensibles deben estar definidos explícitamente como protegidos.

6. Un permiso de modificación no concede automáticamente permiso de lectura, ni viceversa.

7. Cuando un usuario no tenga autorización, la operación debe denegarse de forma segura, sin depender del comportamiento visual del frontend.

8. Los permisos deben evaluarse siempre en el contexto del negocio, tenant y sucursal autorizados cuando corresponda.

9. Para acciones especiales del POS que no son simples operaciones CRUD, como anular ventas, procesar devoluciones, abrir o cerrar cajas y autorizar descuentos extraordinarios, utilizar permisos específicos como `sale:void`, `sale:refund` o `cash_register:close`.

10. Las reglas de negocio, cálculos de importes, impuestos, totales y movimientos de inventario deben seguir siendo responsabilidad exclusiva del backend. Un permiso de edición nunca debe permitir alterar arbitrariamente valores calculados.

## 5. Implementación del frontend

Necesito una interfaz de administración de roles clara, organizada y fácil de utilizar.

### Pantalla de listado de roles

Debe permitir:

- Consultar roles disponibles.
- Buscar por nombre o descripción.
- Filtrar por estado.
- Crear nuevos roles.
- Editar roles existentes.
- Eliminar o desactivar roles cuando corresponda.
- Mostrar únicamente las acciones autorizadas.
- Impedir la administración de roles protegidos cuando el usuario no tenga atribuciones suficientes.

### Pantalla de creación y edición de roles

El formulario deberá tener dos secciones principales.

**Sección 1: Información del rol**

Campos:

- Nombre del rol.
- Descripción.
- Estado activo/inactivo.

Los campos deben respetar sus respectivos permisos granulares.

**Sección 2: Asignación de permisos**

Quiero una interfaz visual basada en módulos, con permisos generales y permisos específicos por atributo.

Ejemplo de presentación:

### Módulo: PRODUCTOS

**Acceso general**

[ ] Ver todo

[ ] Crear

[ ] Editar

[ ] Eliminar

**Acceso a atributos / campos**

| Campo | Ver | Crear | Editar |
|---|:---:|:---:|:---:|
| Nombre | ☐ | ☐ | ☐ |
| Descripción | ☐ | ☐ | ☐ |
| Precio de venta | ☐ | ☐ | ☐ |
| Precio de compra | ☐ | ☐ | ☐ |
| Stock | ☐ | ☐ | ☐ |
| Categoría | ☐ | ☐ | ☐ |
| Estado | ☐ | ☐ | ☐ |

Los atributos y acciones deben generarse dinámicamente desde el catálogo de permisos disponible. No se deben inventar permisos para completar visualmente una tabla.

Cuando una acción no exista para determinado atributo, mostrar una celda deshabilitada o un guion.

### Comportamiento del formulario

- Agrupar automáticamente los permisos por módulo.
- Separar permisos generales y permisos de atributos.
- Mostrar los módulos ordenados.
- Mostrar los atributos ordenados.
- Permitir seleccionar y deseleccionar permisos individuales.
- Mostrar cuáles permisos están seleccionados al editar un rol.
- Mostrar un contador de permisos seleccionados.
- Deshabilitar controles cuando el usuario actual no tenga autorización para modificar los permisos del rol.
- Evitar que un usuario se asigne o conceda permisos superiores a los que está autorizado a administrar.
- Mantener los permisos seleccionados sincronizados con el estado del formulario.
- Ofrecer estados de carga, errores y confirmación de operaciones.

### Aplicación de permisos en el resto del POS

La autorización no debe limitarse al formulario de roles.

Los módulos del POS deben consultar los permisos efectivos del usuario.

Por ejemplo, si un cajero tiene permiso para modificar cantidades, pero no precios:

- El campo cantidad podrá editarse.
- El campo precio unitario estará bloqueado o será de solo lectura.
- Los controles para modificar descuentos no deben estar disponibles sin autorización.
- La interfaz deberá reflejar los permisos vigentes del usuario.

Cuando un atributo carezca de permiso de lectura, el frontend no debe mostrar su contenido.

Implementa mecanismos reutilizables para verificar permisos en componentes, formularios, menús, rutas y acciones, evitando repetir comprobaciones manuales por todo el código.

## 6. Implementación del backend

El backend será la autoridad definitiva de la autorización.

El frontend solamente representará los permisos y facilitará la experiencia del usuario, pero nunca será responsable de garantizar la seguridad.

El backend debe:

- Identificar al usuario autenticado.
- Resolver sus roles activos.
- Obtener sus permisos efectivos.
- Verificar permisos generales antes de ejecutar una operación.
- Verificar permisos granulares antes de leer, crear o actualizar campos protegidos.
- Rechazar operaciones no autorizadas con respuestas HTTP apropiadas, como `403 Forbidden`.
- Filtrar los atributos protegidos de las respuestas de la API.
- Validar explícitamente los campos recibidos en las solicitudes.
- Impedir modificaciones indirectas o asignaciones masivas de campos no autorizados.
- Aplicar restricciones de tenant, negocio y sucursal.
- Proteger roles administrativos y de sistema.
- Registrar operaciones sensibles cuando corresponda.

### Seguridad a nivel de atributos

Considera un usuario que tiene:

```text
product:update
product:update:name
```

Si envía una solicitud como esta:

```json
{
  "name": "Arroz Premium",
  "purchase_price": 10,
  "sale_price": 25
}
```

El backend debe aceptar únicamente los campos que esté autorizado a modificar y rechazar la solicitud si contiene campos protegidos no permitidos, indicando el error apropiado.

No debe confiar en que el frontend haya deshabilitado los campos.

Asimismo, si no tiene permiso para leer `purchase_price`, ese atributo no debe aparecer en la respuesta serializada de la API.

Esta protección también debe aplicarse a listados, detalles, exportaciones, reportes y otros canales por los que se entregue información.

## 7. Base de datos y relaciones

Analiza el esquema actual y define o adapta las entidades necesarias para representar:

- Usuarios.
- Roles.
- Permisos.
- Relación entre usuarios y roles.
- Relación entre roles y permisos.
- Relación con tenants o negocios, cuando corresponda.

Utiliza claves foráneas, restricciones de unicidad e índices apropiados.

Debe ser posible agregar nuevos módulos y nuevos permisos sin rediseñar el sistema.

El catálogo de permisos debe admitir sincronización mediante migraciones o seeds controlados, evitando duplicados.

Si el proyecto ya utiliza Supabase, PostgreSQL u otro motor, aprovecha su arquitectura actual. Si utiliza políticas de seguridad de filas (RLS), intégralas con las reglas de acceso por tenant y usuario; no consideres RLS como reemplazo automático de la autorización granular de columnas.

## 8. Seguridad administrativa y escalamiento de privilegios

Este punto es especialmente importante.

Un usuario con permiso para administrar roles no debe poder:

- Concederse permisos superiores mediante la creación o modificación de un rol.
- Asignar permisos que no está autorizado a administrar.
- Modificar roles protegidos.
- Convertirse en superadministrador modificando solicitudes HTTP.
- Acceder a roles de otros negocios.
- Alterar indirectamente sus permisos aprovechando relaciones entre usuarios y roles.

El sistema debe distinguir entre el superadministrador de plataforma y los administradores de negocios, si existe una arquitectura multiempresa.

El superadministrador de plataforma debe ser una identidad especialmente protegida, no un rol que cualquier administrador pueda crear asignándose todos los permisos.

Cualquier cambio de permisos debe reflejarse correctamente en las sesiones afectadas, mediante invalidación, actualización o un mecanismo seguro de resolución de permisos.

## 9. Organización y reutilización

Quiero una solución modular y mantenible.

En el frontend, centraliza la comprobación de permisos mediante servicios, utilidades, guards o directivas reutilizables, según las capacidades del framework existente.

En el backend, centraliza la autorización mediante políticas, servicios, guards o middleware adecuados al framework.

Evita duplicar condiciones de permisos por todo el sistema.

No quiero que cada módulo implemente su propia lógica de autorización de forma independiente.

La incorporación de un nuevo módulo debe consistir principalmente en registrar sus recursos, acciones, campos protegidos y permisos correspondientes.

## 10. Pruebas de funcionamiento

Implementa pruebas que verifiquen, como mínimo:

- Que un usuario con `product:read` puede consultar productos del negocio autorizado.
- Que un usuario sin `product:update` no puede modificar productos.
- Que un usuario con `product:update` pero sin `product:update:sale_price` no puede modificar el precio protegido.
- Que un usuario sin permiso de lectura de un campo protegido no recibe ese campo desde la API.
- Que el backend rechaza campos protegidos no autorizados incluso si se envían manualmente.
- Que un administrador no puede conceder permisos superiores a los permitidos.
- Que los roles protegidos no pueden modificarse desde cuentas no autorizadas.
- Que un usuario de un tenant no puede acceder a información de otro tenant.
- Que los permisos se aplican correctamente al crear y editar.
- Que los cambios en roles se reflejan en los permisos efectivos de los usuarios.
- Que las acciones especiales del POS respetan sus permisos específicos.

## 11. Criterios de aceptación

Consideraré que la funcionalidad está implementada cuando pueda:

1. Crear un rol personalizado desde la interfaz.
2. Seleccionar los módulos a los que tendrá acceso.
3. Elegir las operaciones generales que puede realizar en cada módulo.
4. Elegir qué atributos puede ver, crear o editar.
5. Guardar los permisos del rol en la base de datos.
6. Asignar ese rol a un usuario.
7. Iniciar sesión con ese usuario y comprobar que la interfaz respeta sus permisos.
8. Comprobar que el backend rechaza cualquier intento de operación no autorizada, incluso manipulando solicitudes HTTP.
9. Modificar los permisos de un rol y verificar que se actualicen correctamente.
10. Incorporar nuevos módulos y atributos sin tener que reconstruir toda la arquitectura de autorización.

## 12. Instrucciones finales de implementación

Primero inspecciona el repositorio e identifica qué funcionalidades de autenticación, usuarios, roles, permisos y administración ya existen.

Reutiliza la estructura y los patrones existentes en lugar de crear sistemas paralelos.

Después implementa la solución de extremo a extremo: esquema de datos, migraciones, servicios backend, endpoints, validaciones, autorización, servicios frontend, componentes, formularios y pruebas.

Si el proyecto aún no contiene todos los módulos comerciales del POS, implementa una base de autorización reutilizable e intégrala con los módulos existentes. No construyas módulos comerciales nuevos solamente para demostrar el sistema de permisos.

No te limites a explicar cómo implementarlo: realiza los cambios necesarios en el código existente.

Al terminar, documenta:

- Los archivos creados o modificados.
- El esquema de permisos.
- Los endpoints utilizados.
- Las migraciones que deban ejecutarse.
- Cómo registrar permisos para un nuevo módulo.
- Cómo aplicar los permisos generales y granulares.
- Las pruebas realizadas y sus resultados.
- Las limitaciones o tareas pendientes.

**Resultado esperado:** Un sistema POS con RBAC dinámico y autorización granular por atributos, consistente entre frontend, backend y base de datos, seguro, reutilizable y preparado para incorporar nuevos módulos.