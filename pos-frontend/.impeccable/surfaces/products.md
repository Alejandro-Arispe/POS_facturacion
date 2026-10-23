---
primaryTarget: src/app/features/companies/sections/products/pages/catalog/my-catalog/my-catalog.html
relatedTargets:
  - src/app/features/companies/sections/products/pages/catalog/my-catalog/my-catalog.ts
  - src/app/features/companies/sections/products/pages/catalog/my-catalog/my-catalog.css
---

# Lista de productos

Ruta: `/company/:id/products`. Modo: Operate.

## Direction contract

**THESIS.** Un catálogo que se puede recorrer y consultar como una tabla de
operaciones: encontrar un producto y acceder a sus acciones con pocos pasos.

**OWN-WORLD.** Mantener el shell blanco y azul ya acordado. La nueva referencia
`recursos/lista_producto.PNG` (en la raíz del repositorio) aporta tabla compacta, miniaturas, separadores
discretos, controles de columna y paginación; sus datos de transacciones no se
convierten en campos del producto.

**STORY.** Entrar directamente a la lista, buscar o filtrar, ordenar una columna,
ver un producto, editarlo según permisos o exportar los registros consultados.
El registro existente sigue disponible mediante Nuevo producto.

**FIRST VIEWPORT.** Un único panel de catálogo con título, empresa y acciones
arriba; búsqueda y selección debajo; cabecera de tabla tenue, ocho filas y pie
con cantidad de registros y navegación. Mantener columnas reales de producto.

**SIGNATURE.** Las miniaturas junto al nombre y código permiten reconocer cada
producto; la personalización de columnas y la exportación usan las mismas
restricciones de campos que el catálogo.

**STATES.** Foco visible, menús cerrables con Escape, carga, error con reintento,
catálogo vacío, filtros sin coincidencias e imágenes ausentes. En móvil la barra
se reorganiza y el desplazamiento horizontal se limita a la tabla.
