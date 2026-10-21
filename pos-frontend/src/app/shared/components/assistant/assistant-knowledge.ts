/**
 * Base de conocimiento del asistente de ayuda. Cada tema tiene palabras clave (sin tildes, en minusculas),
 * una respuesta paso a paso y, opcionalmente, la pantalla a la que conviene ir.
 */
export interface AssistantTopic {
  id: string;
  question: string;
  keywords: string[];
  answer: string[];
  /** Ruta relativa a la empresa activa (company/:id/...). */
  companyRoute?: string[];
  /** Ruta absoluta cuando no depende de la empresa. */
  route?: string[];
  queryParams?: Record<string, string>;
}

export const ASSISTANT_TOPICS: AssistantTopic[] = [
  {
    id: 'abrir-caja',
    question: '¿Cómo abro una caja?',
    keywords: ['abrir caja', 'apertura', 'abrir turno', 'iniciar caja', 'monto inicial', 'caja'],
    answer: [
      'Entra a la empresa y elige la sucursal en "Sucursales".',
      'En el menú de la sucursal abre "Cajas".',
      'Pulsa "Abrir" en la caja que vas a usar e ingresa el monto inicial de efectivo.',
      'Al confirmar se abre el punto de venta (POS) con tu sesión de caja.',
    ],
    companyRoute: ['branches'],
  },
  {
    id: 'cerrar-caja',
    question: '¿Cómo cierro la caja y hago el arqueo?',
    keywords: ['cerrar caja', 'cierre', 'arqueo', 'cuadrar', 'faltante', 'sobrante', 'fin de turno'],
    answer: [
      'Desde el POS o desde "Cajas", elige "Cerrar caja".',
      'El sistema muestra lo esperado por método de pago (efectivo, QR, etc.).',
      'Ingresa el monto real contado; verás si hay sobrante o faltante.',
      'Confirma el cierre: la caja queda libre y el movimiento queda en la bitácora.',
    ],
    companyRoute: ['branches'],
  },
  {
    id: 'venta',
    question: '¿Cómo registro una venta?',
    keywords: ['venta', 'vender', 'cobrar', 'pos', 'punto de venta', 'factura', 'ticket', 'carrito'],
    answer: [
      'Abre una caja de la sucursal (ver "¿Cómo abro una caja?").',
      'En el POS busca productos por nombre o código y agrégalos al carrito.',
      'Si el cliente es mayorista, selecciónalo: su descuento de categoría se aplica solo.',
      'Elige el método de pago y confirma; puedes imprimir o reenviar la factura.',
    ],
    companyRoute: ['branches'],
  },
  {
    id: 'descuento',
    question: '¿Cómo aplico un descuento?',
    keywords: ['descuento', 'rebaja', 'promocion', 'porcentaje'],
    answer: [
      'En el carrito del POS, cada producto tiene un campo de descuento en % o en monto fijo.',
      'Solo los usuarios con el permiso "Aplicar descuentos" pueden modificarlo.',
      'Los clientes con categoría (por ejemplo Mayorista) reciben su descuento base automáticamente.',
    ],
  },
  {
    id: 'credito',
    question: '¿Cómo vendo a crédito y cobro después?',
    keywords: ['credito', 'fiado', 'deuda', 'cuenta por cobrar', 'cobro de creditos', 'saldo'],
    answer: [
      'Al vender, elige el tipo de venta "Crédito" y un cliente con límite de crédito disponible.',
      'Para cobrar, ve a la caja abierta y entra a "Cobro de Creditos".',
      'Selecciona la cuenta pendiente del cliente y registra el pago.',
    ],
    companyRoute: ['clients'],
  },
  {
    id: 'producto',
    question: '¿Cómo creo un producto?',
    keywords: ['producto', 'crear producto', 'nuevo producto', 'articulo', 'precio', 'codigo de barras', 'catalogo', 'imagen'],
    answer: [
      'Primero crea una categoría y subcategoría en "Productos > Categoria".',
      'Luego en "Productos > Catalogo" pulsa registrar producto.',
      'Completa nombre, código de barras, unidad, precio y opcionalmente una imagen.',
      'El producto aparece en todas las sucursales con stock 0 hasta que registres una entrada.',
    ],
    companyRoute: ['products'],
  },
  {
    id: 'categorias',
    question: '¿Cómo organizo las categorías de productos?',
    keywords: ['categoria de producto', 'subcategoria', 'categorias', 'clasificar', 'rubro'],
    answer: [
      'Ve a "Productos > Categoria".',
      'Crea la categoría (por ejemplo Lácteos) y dentro sus subcategorías.',
      'Cada producto pertenece a una subcategoría.',
    ],
    companyRoute: ['products', 'categories'],
  },
  {
    id: 'stock',
    question: '¿Cómo cargo stock o hago un ajuste de inventario?',
    keywords: ['stock', 'inventario', 'existencia', 'entrada', 'salida', 'ajuste', 'merma', 'kardex', 'reponer', 'movimiento'],
    answer: [
      'Entra a la sucursal y abre "Inventario".',
      'En la pestaña de movimientos registra una "Entrada manual", "Salida", "Ajuste" o "Merma".',
      'En la pestaña de stock puedes definir el stock mínimo y máximo de cada producto.',
      'Cuando un producto baja del mínimo recibirás una alerta.',
    ],
    companyRoute: ['branches'],
  },
  {
    id: 'empleados',
    question: '¿Cómo agrego empleados?',
    keywords: ['empleado', 'personal', 'invitar', 'cajero', 'agregar usuario', 'trabajador', 'staff'],
    answer: [
      'El empleado primero debe registrarse en el sistema con su correo.',
      'Ve a "Usuarios > Personal" y elige "Invitar personal".',
      'Indica su correo, el rol y las sucursales donde trabajará.',
      'El empleado recibe un enlace de invitación; al aceptarlo ya puede operar.',
    ],
    companyRoute: ['users', 'staff'],
  },
  {
    id: 'roles',
    question: '¿Cómo creo roles y permisos?',
    keywords: ['rol', 'roles', 'permiso', 'permisos', 'privilegio', 'acceso', 'restringir'],
    answer: [
      'Ve a "Usuarios > Roles".',
      'Crea un rol y marca los permisos que tendrá, agrupados por módulo.',
      'Los permisos controlan tanto las funciones (ver, crear, editar) como los botones y formularios visibles.',
      'Asigna el rol a tu personal desde "Personal".',
    ],
    companyRoute: ['users', 'rols'],
  },
  {
    id: 'clientes',
    question: '¿Cómo registro clientes mayoristas?',
    keywords: ['cliente', 'clientes', 'mayorista', 'categoria de cliente', 'limite de credito', 'agenda'],
    answer: [
      'Crea la categoría en "Clientes > Categorias" (por ejemplo Mayorista con descuento y plazo de crédito).',
      'En "Clientes > Agenda" invita al cliente por su correo.',
      'Luego edita al cliente y asígnale la categoría y su límite de crédito.',
    ],
    companyRoute: ['clients'],
  },
  {
    id: 'pedidos',
    question: '¿Cómo funcionan los pedidos?',
    keywords: ['pedido', 'pedidos', 'orden', 'encargo'],
    answer: [
      'Los clientes mayoristas pueden hacer pedidos que llegan a la bandeja "Pedidos".',
      'Desde allí cambias su estado (en preparación, listo) y los cargas al POS para cobrarlos.',
    ],
    companyRoute: ['orders'],
  },
  {
    id: 'reportes',
    question: '¿Qué reportes tiene el sistema?',
    keywords: ['reporte', 'reportes', 'informe', 'estadistica', 'excel', 'pdf', 'exportar'],
    answer: [
      'Estáticos: reportes clásicos de ventas, inventario y cajas, exportables a PDF y Excel.',
      'Parametrizados: los mismos reportes filtrando por fechas, sucursal, producto, etc.',
      'Consulta QBE: armas tu propio reporte eligiendo columnas y condiciones.',
      'Dinámicos: escribes o dictas por voz lo que quieres ver ("ventas por sucursal del mes").',
      'Análisis inteligente: indicadores, pronóstico y recomendaciones calculadas con Python.',
    ],
    companyRoute: ['reports', 'static'],
  },
  {
    id: 'qbe',
    question: '¿Cómo armo un reporte a medida (QBE)?',
    keywords: ['qbe', 'a medida', 'personalizado', 'consulta', 'criterio', 'filtro', 'columnas'],
    answer: [
      'Ve a "Reportes > Consulta QBE".',
      'Elige la fuente de datos (ventas, productos, inventario, clientes...).',
      'Marca las columnas que quieres ver y agrega condiciones como "precio mayor a 10".',
      'Ejecuta la consulta y exporta el resultado a Excel si lo necesitas.',
    ],
    companyRoute: ['reports', 'qbe'],
  },
  {
    id: 'voz',
    question: '¿Cómo pido un reporte por voz?',
    keywords: ['voz', 'hablar', 'dictar', 'microfono', 'reporte por voz'],
    answer: [
      'Ve a "Reportes > Dinamicos".',
      'Pulsa el botón del micrófono y di lo que necesitas, por ejemplo "productos más vendidos".',
      'El sistema interpreta tu pedido, genera el reporte y puede leerte el resumen en voz alta.',
    ],
    companyRoute: ['reports', 'dynamic'],
  },
  {
    id: 'dashboard',
    question: '¿Dónde veo el análisis del negocio?',
    keywords: ['dashboard', 'panel', 'indicadores', 'kpi', 'analisis', 'inteligencia de negocios', 'tendencia', 'pronostico', 'prediccion'],
    answer: [
      'Ve a "Reportes > Análisis inteligente".',
      'Verás ingresos, ticket promedio y crecimiento frente al periodo anterior.',
      'El servicio de analítica (Python) calcula la tendencia, el pronóstico de los próximos 14 días y el análisis ABC de productos.',
      'Al final encontrarás recomendaciones automáticas: mejor día, hora pico, productos de baja rotación y stock en riesgo.',
    ],
    companyRoute: ['reports', 'analytics'],
  },
  {
    id: 'configuracion',
    question: '¿Cómo cambio el idioma, la moneda o los colores?',
    keywords: ['idioma', 'ingles', 'portugues', 'moneda', 'dolar', 'color', 'tema', 'oscuro', 'personalizar', 'configuracion', 'apariencia'],
    answer: [
      'Ve a "Configuración".',
      'En "General" eliges idioma (español, inglés, portugués), moneda y zona horaria.',
      'En "Apariencia" eliges tema claro u oscuro y el color de acento.',
      'Guarda: los cambios se aplican a toda la empresa. El idioma también se cambia rápido arriba a la derecha.',
    ],
    route: ['/administrator/settings'],
  },
  {
    id: 'bitacora',
    question: '¿Cómo veo quién hizo cada cambio?',
    keywords: ['bitacora', 'auditoria', 'historial', 'quien', 'registro de actividad', 'log', 'seguridad'],
    answer: [
      'Ve a "Bitácora" en el menú de la empresa.',
      'Verás cada inicio de sesión y cada operación: usuario, fecha, módulo e IP.',
      'Los registros no se pueden editar ni borrar; el SuperAdmin puede verificar su integridad.',
    ],
    companyRoute: ['audit-log'],
  },
  {
    id: 'plan',
    question: '¿Cómo pago o cambio mi plan?',
    keywords: ['plan', 'suscripcion', 'pagar', 'pago', 'basico', 'pro', 'premium', 'mensualidad', 'saas'],
    answer: [
      'En "Mis empresas", si la empresa no tiene plan verás el botón "Pagar Plan".',
      'Elige el plan (Básico, Pro o Premium) y completa el pago.',
      'Cada plan habilita distintos módulos y límites de sucursales y usuarios.',
    ],
    route: ['/my-companies'],
  },
  {
    id: 'sucursal',
    question: '¿Cómo creo una sucursal?',
    keywords: ['sucursal', 'sucursales', 'tienda', 'local', 'agencia'],
    answer: [
      'Entra a la empresa y abre "Sucursales".',
      'Usa la pestaña de registro, completa nombre, dirección, ciudad y teléfono.',
      'Tu plan define cuántas sucursales puedes tener.',
    ],
    companyRoute: ['branches'],
  },
  {
    id: 'notificaciones',
    question: '¿Cómo recibo notificaciones?',
    keywords: ['notificacion', 'notificaciones', 'alerta', 'aviso', 'push', 'campana'],
    answer: [
      'Al iniciar sesión el navegador te pedirá permiso para notificaciones: acéptalo.',
      'Recibirás alertas push de stock bajo, pedidos y caja aunque no estés en la pantalla.',
      'La campana de la barra superior guarda el historial de alertas.',
    ],
  },
  {
    id: 'contrasena',
    question: 'Olvidé mi contraseña',
    keywords: ['contrasena', 'password', 'olvide', 'clave', 'recuperar', 'acceso'],
    answer: [
      'En la pantalla de inicio de sesión pulsa "Olvide mi contrasena".',
      'Ingresa tu correo y recibirás una contraseña nueva.',
    ],
    route: ['/login'],
  },
];

export function normalizeText(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9ñ ]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Devuelve los temas ordenados por relevancia para la pregunta. */
export function searchTopics(query: string): AssistantTopic[] {
  const text = ` ${normalizeText(query)} `;
  const words = text.trim().split(' ').filter((w) => w.length > 2);
  const scored = ASSISTANT_TOPICS.map((topic) => {
    let score = 0;
    for (const keyword of topic.keywords) {
      if (text.includes(` ${keyword} `) || text.includes(keyword)) {
        score += keyword.includes(' ') ? 3 : 2;
      }
    }
    const question = normalizeText(topic.question);
    for (const word of words) {
      if (question.includes(word)) score += 1;
    }
    return { topic, score };
  });
  return scored
    .filter((s) => s.score > 0)
    .sort((a, b) => b.score - a.score)
    .map((s) => s.topic);
}
