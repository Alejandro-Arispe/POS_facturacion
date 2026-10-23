# Sistema visual del POS

Dirección solicitada por el usuario: adaptar el área operativa a la referencia
`../recursos/original-b27b9754366211fc9df46536899f9471.webp`.

- Superficies blancas, fondo frío muy claro y líneas de separación discretas.
- Azul como acento de navegación, selección y series de ventas. Verde, ámbar y
  rojo comunican estados; no sustituyen sus etiquetas.
- Sidebar persistente de 256 px en escritorio, navegación con icono y texto,
  grupos desplegables y selección azul. En móvil se convierte en un panel
  desplegable con cierre por teclado, fondo y navegación.
- Barra superior de 88 px, contexto de página y acciones de cuenta.
- Tipografía de interfaz: Manrope servida localmente con Fontsource, con alternativas de sistema; números
  tabulares, jerarquía clara y etiquetas legibles.
- Paneles con borde de 1 px y radio de 16 px; sin vidrio ni sombras decorativas.
- Tailwind CSS para composición, espaciado, tipografía y estados. Variables
  semánticas compartidas para mantener el tema oscuro y las pantallas existentes.
- Iconos Lucide de trazo uniforme; los controles con solo icono tienen nombre
  accesible. Los iconos decorativos se ocultan a lectores de pantalla.
- Mantener rutas, permisos, traducciones, preferencias, notificaciones y datos
  del backend. Los gráficos representan datos recibidos, incluidos los ceros.

Esta dirección sustituye el tratamiento anterior del shell y del dashboard.
La referencia guía la composición; sus nombres, valores y porcentajes no son
contenido del producto.
