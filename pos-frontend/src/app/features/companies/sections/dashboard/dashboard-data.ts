export interface DailySale {
  fecha: string;
  total: number;
}

/** Normaliza la serie recibida y conserva los dias sin ventas como ceros. */
export function dailySales(points: DailySale[], days: number, endDate: string): DailySale[] {
  const totals = new Map<string, number>();
  for (const point of points) {
    if (/^\d{4}-\d{2}-\d{2}$/.test(point.fecha) && Number.isFinite(Number(point.total))) {
      totals.set(point.fecha, (totals.get(point.fecha) ?? 0) + Number(point.total));
    }
  }
  const end = new Date(endDate + 'T00:00:00Z');
  if (Number.isNaN(end.getTime())) return [];
  return Array.from({ length: days }, (_, index) => {
    const date = new Date(end);
    date.setUTCDate(date.getUTCDate() - days + index + 1);
    const key = date.toISOString().slice(0, 10);
    return { fecha: key, total: totals.get(key) ?? 0 };
  });
}
