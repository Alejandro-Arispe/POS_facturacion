import axios from 'axios';

export interface NotificationDetailField {
  label: string;
  value: string;
}

export interface StockDisplay {
  sucursal_nombre: string | null;
  producto_nombre: string | null;
  cantidad: string | number | null;
}

export interface NotificationViewModel {
  id: string | number | null;
  titulo: string;
  mensaje: string;
  fecha: string;
  fechaLegible: string;
  sortAt: Date;
  leido: boolean;
  payload: any;
  display: StockDisplay;
  esAlertaStock: boolean;
}

export interface NotificationDetailModel {
  titulo: string;
  mensaje: string;
  fecha: string;
  fechaLegible: string;
  payload: any;
  fields: NotificationDetailField[];
}

export async function fetchStockAlertHistory(apiBase: string, empresaIds: number[]): Promise<any[]> {
  if (empresaIds.length === 0) {
    return [];
  }

  const responses = await Promise.all(
    empresaIds.map((idEmpresa) => axios.get(`${apiBase}/notifications/history/empresas/${idEmpresa}`)),
  );

  return responses.flatMap((res) => res.data.items || []);
}

export async function markStockAlertAsRead(apiBase: string, id: string | number | null): Promise<void> {
  if (id === null || id === undefined) {
    return;
  }

  await axios.post(`${apiBase}/notifications/mark-read`, { id });
}

export function shouldHandleStockAlertEvent(type: any): boolean {
  return type === 'STOCK_ALERT' || type === 'NOTIFICATION_CLICK';
}

export function findLatestNewUnreadStockAlert(
  items: NotificationViewModel[],
  previousIds: Set<string>,
): NotificationViewModel | null {
  const found = items.find(
    (item) => !previousIds.has(String(item.id)) && item.esAlertaStock && !item.leido,
  );
  return found || null;
}

export function normalizeStockNotification(item: any): NotificationViewModel {
  const payload = normalizePayload(item.payload);
  const display = extractStockDisplay(payload);
  const fecha = item.fecha || item.created_at || item.createdAt || new Date().toISOString();
  const titulo = item.titulo || payload.title || (display.sucursal_nombre ? `Stock under "${display.sucursal_nombre}"` : 'Notification');
  const esAlertaStock = Boolean(
    display.sucursal_nombre ||
      display.producto_nombre ||
      display.cantidad !== null ||
      /stock under/i.test(String(titulo)),
  );

  return {
    id: item.id ?? null,
    titulo,
    mensaje: item.mensaje || payload.body || payload.message || '',
    fecha,
    fechaLegible: formatDateTime(fecha),
    sortAt: new Date(fecha),
    leido: !!item.leido,
    payload,
    display,
    esAlertaStock,
  };
}

export function buildIncomingStockNotification(payload: any): NotificationViewModel {
  const normalizedPayload = normalizeIncomingPayload(payload);
  return normalizeStockNotification({
    id: normalizedPayload.id ?? null,
    titulo: normalizedPayload.title || 'Notification',
    mensaje: normalizedPayload.body || normalizedPayload.message || '',
    fecha: new Date().toISOString(),
    leido: false,
    payload: normalizedPayload,
  });
}

export function buildStockDetailModel(item: NotificationViewModel): NotificationDetailModel {
  const payload = item.payload || {};
  const productoFallback = payload.producto_id ?? payload.id_producto ?? 'N/A';

  return {
    titulo: item.titulo || (item.display.sucursal_nombre ? `Stock under "${item.display.sucursal_nombre}"` : 'Notification'),
    mensaje: item.mensaje,
    fecha: item.fecha,
    fechaLegible: item.fechaLegible,
    payload,
    fields: [
      { label: 'Branch', value: item.display.sucursal_nombre || 'N/A' },
      { label: 'Product', value: item.display.producto_nombre || String(productoFallback) },
      { label: 'Quantity', value: item.display.cantidad === null ? 'N/A' : String(item.display.cantidad) },
      { label: 'Date', value: item.fechaLegible || item.fecha || 'N/A' },
    ],
  };
}

function normalizeIncomingPayload(payload: any): any {
  const nestedData = typeof payload?.data === 'string' ? tryParseJson(payload.data) : payload?.data;
  const baseData = nestedData && typeof nestedData === 'object' ? nestedData : {};
  return {
    ...baseData,
    ...payload,
    title: payload?.title || payload?.notification?.title || baseData.title || null,
    body: payload?.body || payload?.notification?.body || baseData.body || null,
  };
}

function normalizePayload(payload: any): any {
  if (typeof payload === 'string') {
    const parsed = tryParseJson(payload);
    return parsed && typeof parsed === 'object' ? parsed : {};
  }

  if (!payload || typeof payload !== 'object') {
    return {};
  }

  const nestedData = typeof payload.data === 'string' ? tryParseJson(payload.data) : payload.data;
  const baseData = nestedData && typeof nestedData === 'object' ? nestedData : {};

  return {
    ...baseData,
    ...payload,
  };
}

function extractStockDisplay(payload: any): StockDisplay {
  return {
    sucursal_nombre:
      payload.sucursal_nombre ||
      payload.nombre_sucursal ||
      payload.sucursal ||
      payload.sucursal?.nombre ||
      payload.sucursal?.nombre_sucursal ||
      payload.branch_name ||
      null,
    producto_nombre:
      payload.producto_nombre ||
      payload.nombre_producto ||
      payload.producto ||
      payload.producto?.nombre ||
      payload.product?.nombre ||
      payload.product_name ||
      null,
    cantidad:
      payload.cantidad ??
      payload.stock_actual ??
      payload.cantidad_actual ??
      payload.qty ??
      payload.quantity ??
      null,
  };
}

function tryParseJson(value: any) {
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function formatDateTime(value: string | Date): string {
  let date: Date;
  if (value instanceof Date) {
    date = value;
  } else if (typeof value === 'string') {
    const isoNoTZ = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;
    const normalized = isoNoTZ.test(value) ? `${value}Z` : value;
    date = new Date(normalized);
  } else {
    date = new Date(String(value));
  }

  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return new Intl.DateTimeFormat('es-ES', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}