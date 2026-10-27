# Migraciones RBAC

| Versión | Conversión | Implementación |
|---|---|---|
| 1 | Metadatos canónicos, ámbitos, restricciones e índices; estados históricos de campos | `RbacMigration.migrate` |
| 2 | Catálogo de campos de producto y conversión de permisos heredados no financieros | `RbacMigration.migrateProducts` |
| 3 | Permisos comerciales y ámbito de empresa de los perfiles de clientes | `RbacMigration.migrateCommercial` |

El runner del catálogo ejecuta las tres versiones en orden. La tabla `rbac_migration`
evita repetir conversiones y restaurar permisos revocados. No se deben insertar
marcadores manualmente ni eliminarlos para repetir la migración.

Los scripts `postgresql/V001__schema.sql` y `h2/V001__schema.sql` preparan el esquema
inicial para despliegues que no emplean `ddl-auto: update`. No ejecutan las
conversiones de concesiones. V2 añade datos de catálogo; no necesita columnas
nuevas y se implementa en Java para compartir las reglas con los fixtures H2.

V2 conserva costo/precio y cualquier atributo registrado previamente. Solo
convierte campos nuevos que heredaban acciones generales activas. La delegación
se deriva de esa misma concesión general. Agregar permisos posteriores requiere
otra versión explícita, no repetir V1/V2.

Los fixtures de migración y tests de productos se ejecutan con `./mvnw.cmd test`.
La validación con fixtures y recuperación en PostgreSQL se documenta en
`recursos/RBAC_FASE_8.md`. Una base real requiere revisar sus diagnósticos propios.
El contrato completo está en `recursos/RBAC_FASES_1_3.md` y
`recursos/RBAC_FASES_4_5.md`, en la raíz del repositorio.

V3 añade `cliente.id_empresa`, clave foránea e índice. Con `ddl-auto: update` el
modelo prepara el esquema; sin él, ejecutar previamente el script V003 del motor
correspondiente. La conversión Java usa categoría o una única empresa con rol
CLIENTE protegido; conserva nulos los casos ambiguos para revisión. No amplía una
misma ficha a varias empresas. El contrato de operaciones está en
`recursos/RBAC_FASE_6.md`.

## Catálogo histórico y canónico en el arranque

El seeder reutiliza permisos por su código físico histórico/canónico o por
`codigo_canonico`. `RbacMigration` consolida equivalentes antes de asignar claves
únicas, conservando el ID canónico existente y reasociando sus concesiones de
forma restrictiva. La reparación también se aplica con V1 ya registrada y no
requiere borrar marcadores ni cambiar índices. Ver
`recursos/RBAC_CORRECCION_CATALOGO.md` en la raíz.
