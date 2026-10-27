# Corrección del arranque: códigos canónicos duplicados

El fallo `codigo_canonico = inventory_movement:read` se reprodujo en una prueba
que simula una base existente con el código físico canónico y sin el marcador V1.
El seeder solo comprobaba `MOVIMIENTO_VER`, creaba otra fila y V1 intentaba darles
el mismo código canónico. La consulta ejecutada dentro de `RbacMigration.add`
provocaba el autoflush de esos cambios pendientes; por eso el error aparecía en
ese método aunque el conflicto procediera de la normalización anterior.

## Corrección

- El seeder comprueba el código histórico, el físico canónico y `codigo_canonico`
  antes de crear una fila. La plantilla inicial de empleado admite ambos formatos.
- El bloqueo transaccional PostgreSQL se adquiere antes del seeder, incluyendo la
  creación del catálogo, y no solamente cuando empieza la conversión.
- La normalización agrupa equivalentes antes de actualizar claves únicas. Conserva
  el ID que ya tiene el código canónico; si no existe, conserva el más antiguo.
- Si ya hay duplicados persistidos, reasocia `rol_permiso` al permiso conservado y
  elimina las filas redundantes después de liberar sus relaciones. Si un rol tenía
  concesiones contradictorias, prevalece la revocación; delegación también requiere
  que todas sus filas la autoricen. Un permiso inactivo no se reactiva por fusionarlo.
- La reparación es idempotente y se ejecuta incluso si V1 ya estaba registrada.
  No vuelve a conceder capacidades por reiniciar. Los marcadores siguen siendo V1–V3.
- Dos códigos canónicos distintos en una misma fila se consideran una inconsistencia
  y detienen el arranque con el ID afectado, en lugar de reasociar permisos ajenos.

Las pruebas incluyen el mismo fallo original, código físico canónico sin metadatos,
catálogo mixto con relaciones existentes, revocación y permiso inactivo. Usan bases
ficticias; no se accede a la base habitual del usuario ni se modifican sus cuentas.

## Arranque

Desde `pos-backend`:

```powershell
./mvnw.cmd spring-boot:run
```

La reparación se aplica dentro de la transacción del arranque. No requiere borrar
la base, desactivar la restricción única ni modificar `rbac_migration` manualmente.

## Verificación

Resultado final: **112 pruebas de backend aprobadas en H2**, **93 pruebas RBAC
aprobadas en PostgreSQL 17.4**, incluyendo las cuatro regresiones del catálogo,
y JAR generado correctamente. El respaldo/restauración de la copia también pasó,
con las mismas concesiones y marcadores 1, 2, 3. La instancia temporal quedó detenida.

Evidencia local:

- `pos-backend/target/rbac-catalog-reproduction.log`: fallo original reproducido
  antes de la corrección con la misma clave `inventory_movement:read`.
- `pos-backend/target/rbac-catalog-package.log` y `target/rbac-catalog-h2-reports`.
- `pos-backend/target/rbac8-drill-3bc552c9afff426fa5feb8694e410d7d`:
  siete reportes JUnit, resultado, diagnóstico y respaldo del ensayo PostgreSQL.
El ensayo reproducible `scripts/Test-RbacPostgres.ps1` incluye la nueva suite
`CatalogUpgradeRbacTests`, además de las suites anteriores y la recuperación del respaldo.
