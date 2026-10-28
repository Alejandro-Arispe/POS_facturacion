import { describe, expect, it } from 'vitest';
import { missingCreationFields, productPayload, redactProduct } from './product-fields';

describe('Campos de producto', () => {
  it('solo envia campos modificados y autorizados, sin completar precios ocultos', () => {
    const body = productPayload(
      { nombre: ' Arroz ', precio: 0, costo: null, descripcion: 'Texto' },
      new Set(['nombre', 'precio', 'costo']),
      'update',
      (action, field) => action === 'update' && field === 'nombre',
    );
    expect(body).toEqual({ nombre: 'Arroz' });
  });
  it('conserva null explicito y omite un campo no tocado', () => {
    expect(
      productPayload(
        { costo: null, descripcion: 'Antes' },
        new Set(['costo']),
        'update',
        () => true,
      ),
    ).toEqual({ costo: null });
    expect(productPayload({ costo: 12 }, new Set(), 'update', () => true)).toEqual({});
  });
  it('crear no hereda permisos de editar o leer', () => {
    expect(
      productPayload(
        { precio: 25, costo: 10 },
        new Set(),
        'create',
        (a, f) => a === 'update' && f === 'precio',
      ),
    ).toEqual({});
    expect(missingCreationFields((a, f) => a === 'create' && f === 'nombre')).toEqual([
      'Subcategoría',
      'Unidad de medida',
      'Precio de venta (Bs)',
    ]);
  });
  it('purga datos almacenados y relaciones al revocar lectura en una sesion abierta', () => {
    const original = {
      id_producto: 1,
      nombre: 'Arroz',
      costo: 12,
      precio: 20,
      id_subcategoria: 2,
      subcategoria: { nombre: 'Oculta' },
    };
    const visible = redactProduct(original, (f) => f === 'nombre');
    expect(visible).toEqual({ id_producto: 1, id_empresa: undefined, nombre: 'Arroz' });
    expect(original.costo).toBe(12);
  });
});
