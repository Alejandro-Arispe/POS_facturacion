export const PRODUCT_FIELDS = [
  { key: 'nombre', label: 'Nombre', kind: 'text', required: true },
  { key: 'descripcion', label: 'Descripción', kind: 'textarea' },
  { key: 'codigo_barra', label: 'Código de barras', kind: 'text' },
  { key: 'id_subcategoria', label: 'Subcategoría', kind: 'category', required: true },
  { key: 'unidad_medida', label: 'Unidad de medida', kind: 'text', required: true },
  { key: 'precio', label: 'Precio de venta (Bs)', kind: 'money', required: true },
  { key: 'costo', label: 'Costo de compra (Bs)', kind: 'money' },
  { key: 'activo', label: 'Producto activo', kind: 'boolean' },
  { key: 'imagen', label: 'Imagen', kind: 'image' },
] as const;
export type ProductField = (typeof PRODUCT_FIELDS)[number]['key'];
export type ProductValues = Partial<Record<ProductField, string | number | boolean | null>>;
export type ProductAction = 'read' | 'create' | 'update';
export type FieldCheck = (action: ProductAction, field: ProductField) => boolean;

/** Never send a field merely because it appeared in a response or a default form. */
export function productPayload(
  values: ProductValues,
  touched: ReadonlySet<ProductField>,
  action: 'create' | 'update',
  can: FieldCheck,
): ProductValues {
  const result: ProductValues = {};
  for (const { key } of PRODUCT_FIELDS) {
    if (
      key === 'imagen' ||
      !can(action, key) ||
      values[key] === undefined ||
      (action === 'update' && !touched.has(key))
    )
      continue;
    const value = values[key];
    result[key] = typeof value === 'string' ? value.trim() : value;
  }
  return result;
}
export function missingCreationFields(can: FieldCheck): string[] {
  return PRODUCT_FIELDS.filter((f) => 'required' in f && f.required && !can('create', f.key)).map(
    (f) => f.label,
  );
}

export function redactProduct<T extends { id_producto: number; id_empresa?: number }>(
  product: T,
  canRead: (field: ProductField) => boolean,
): T {
  const result: Record<string, unknown> = {
    id_producto: product.id_producto,
    id_empresa: product.id_empresa,
  };
  for (const { key } of PRODUCT_FIELDS)
    if (canRead(key) && Object.hasOwn(product, key))
      result[key] = (product as Record<string, unknown>)[key];
  if (canRead('id_subcategoria') && Object.hasOwn(product, 'subcategoria'))
    result['subcategoria'] = (product as Record<string, unknown>)['subcategoria'];
  return result as T;
}
