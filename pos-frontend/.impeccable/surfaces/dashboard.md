# Dashboard de empresa

Ruta: `/company/:id/dashboard`. Modo: Operate.

## Direction contract

**THESIS.** Un resumen comercial que permite identificar ventas, operación de
cajas y problemas de inventario antes de entrar en cada módulo.

**OWN-WORLD.** La referencia blanca y azul aportada por el usuario: navegación
lateral amplia, paneles con borde suave, iconos lineales y cifras tabulares.

**STORY.** Leer primero el estado de hoy, inspeccionar las ventas del periodo y
las alertas de stock, y navegar a sucursales, productos o reportes según permisos.

**FIRST VIEWPORT.** Sidebar fijo a la izquierda, barra superior alineada al área
de trabajo y contenido con separación de 32 px. Encabezado con empresa y fecha,
cuatro indicadores, gráfico de ventas de 30 días y resumen de alertas. En móvil,
navegación desplegable y contenido en una columna.

**SIGNATURE.** El gráfico y su selector de periodo convierten la serie recibida
en una lectura diaria verificable; cada punto expone fecha e importe. Las
alertas de stock conservan sus cantidades y significado, sin porcentajes de
crecimiento inventados.

**MOTION AND STATES.** Cambios breves de color y apertura de navegación; respetar
movimiento reducido. Carga, desconexión recuperable, vacío, ceros reales y foco
de teclado explícitos. Validar escritorio, móvil y navegación con datos reales.
