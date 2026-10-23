import { Producto } from '../../../../../../../core/services/product.service';

export type CatalogProduct = Producto & { costo?: number | null };
export type ColumnKey =
  | 'id_producto'
  | 'nombre'
  | 'codigo_barra'
  | 'subcategoria'
  | 'unidad_medida'
  | 'precio'
  | 'costo'
  | 'activo';
export interface CatalogColumn {
  key: ColumnKey;
  label: string;
}
export interface CatalogQuery {
  search: string;
  status: 'all' | 'active' | 'inactive';
  subcategory: string;
  sort: ColumnKey;
  direction: 'asc' | 'desc';
}

function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

export function productValue(product: CatalogProduct, key: ColumnKey): string | number | null {
  if (key === 'subcategoria') return product.subcategoria?.nombre ?? 'Sin subcategoría';
  if (key === 'activo') return product.activo ? 'Activo' : 'Inactivo';
  return product[key] ?? null;
}

export function queryProducts(products: CatalogProduct[], query: CatalogQuery): CatalogProduct[] {
  const search = normalize(query.search.trim());
  const collator = new Intl.Collator('es', { numeric: true, sensitivity: 'base' });
  return products
    .filter((product) => {
      const matchesSearch =
        !search ||
        normalize(
          [product.nombre, product.codigo_barra ?? '', product.subcategoria?.nombre ?? ''].join(
            ' ',
          ),
        ).includes(search);
      const matchesStatus =
        query.status === 'all' || product.activo === (query.status === 'active');
      const matchesCategory =
        !query.subcategory ||
        String(product.id_subcategoria ?? product.subcategoria?.id_subcategoria ?? '') ===
          query.subcategory;
      return matchesSearch && matchesStatus && matchesCategory;
    })
    .sort((a, b) => {
      const first = productValue(a, query.sort);
      const second = productValue(b, query.sort);
      // Los valores ausentes se mantienen al final en ambos sentidos.
      if (first == null && second != null) return 1;
      if (first != null && second == null) return -1;
      const order =
        typeof first === 'number' && typeof second === 'number'
          ? first - second
          : collator.compare(String(first ?? ''), String(second ?? ''));
      return order ? order * (query.direction === 'asc' ? 1 : -1) : a.id_producto - b.id_producto;
    });
}

function csvCell(value: string | number | null): string {
  let text = value == null ? '' : String(value);
  // Los textos de usuario nunca se interpretan como formulas al abrir el CSV.
  if (typeof value === 'string' && /^[\s\u0000-\u001f]*[=+\-@]/.test(text)) text = "'" + text;
  return '"' + text.replace(/"/g, '""') + '"';
}

export function catalogCsv(products: CatalogProduct[], columns: CatalogColumn[]): string {
  const rows = [
    columns.map((column) => csvCell(column.label)).join(';'),
    ...products.map((product) =>
      columns.map((column) => csvCell(productValue(product, column.key))).join(';'),
    ),
  ];
  return '\uFEFF' + rows.join('\r\n');
}

export function pageNumbers(current: number, total: number): Array<number | '…'> {
  const pages = new Set([1, total, current - 1, current, current + 1]);
  if (current <= 3) [2, 3, 4].forEach((page) => pages.add(page));
  if (current >= total - 2) [total - 3, total - 2, total - 1].forEach((page) => pages.add(page));
  const sorted = [...pages].filter((page) => page >= 1 && page <= total).sort((a, b) => a - b);
  const result: Array<number | '…'> = [];
  for (const page of sorted) {
    const previous = result[result.length - 1];
    if (typeof previous === 'number' && page - previous > 1) result.push('…');
    result.push(page);
  }
  return result;
}
