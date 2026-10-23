import { describe, expect, it } from 'vitest';
import {
  CatalogProduct,
  CatalogQuery,
  catalogCsv,
  pageNumbers,
  queryProducts,
} from './catalog-table';

const products: CatalogProduct[] = [
  {
    id_producto: 1,
    nombre: 'Café molido',
    codigo_barra: 'CAF-10',
    id_subcategoria: 1,
    unidad_medida: 'Unidad',
    precio: 22,
    costo: 14,
    activo: true,
    subcategoria: { id_subcategoria: 1, id_categoria_producto: 1, nombre: 'Bebidas', activo: true },
  },
  {
    id_producto: 2,
    nombre: 'Arroz',
    codigo_barra: 'ARR-20',
    id_subcategoria: 2,
    unidad_medida: 'Kg',
    precio: 9.5,
    costo: null,
    activo: false,
    subcategoria: {
      id_subcategoria: 2,
      id_categoria_producto: 2,
      nombre: 'Abarrotes',
      activo: true,
    },
  },
  {
    id_producto: 3,
    nombre: 'Agua',
    codigo_barra: 'AG-30',
    id_subcategoria: 1,
    unidad_medida: 'Unidad',
    precio: 6,
    costo: 4,
    activo: true,
    subcategoria: { id_subcategoria: 1, id_categoria_producto: 1, nombre: 'Bebidas', activo: true },
  },
];
const query: CatalogQuery = {
  search: '',
  status: 'all',
  subcategory: '',
  sort: 'nombre',
  direction: 'asc',
};

describe('catalog table', () => {
  it('busca sin tildes, por codigo y por subcategoria', () => {
    expect(queryProducts(products, { ...query, search: 'cafe' }).map((p) => p.id_producto)).toEqual(
      [1],
    );
    expect(
      queryProducts(products, { ...query, search: 'arr-20' }).map((p) => p.id_producto),
    ).toEqual([2]);
    expect(
      queryProducts(products, { ...query, search: 'bebidas' }).map((p) => p.id_producto),
    ).toEqual([3, 1]);
  });

  it('combina estado y subcategoria sin modificar los productos originales', () => {
    expect(
      queryProducts(products, { ...query, status: 'active', subcategory: '1' }).map(
        (p) => p.id_producto,
      ),
    ).toEqual([3, 1]);
    expect(queryProducts(products, { ...query, status: 'inactive', subcategory: '1' })).toEqual([]);
    expect(products.map((p) => p.id_producto)).toEqual([1, 2, 3]);
  });

  it('ordena importes numericamente y deja los costos ausentes al final', () => {
    expect(
      queryProducts(products, { ...query, sort: 'precio', direction: 'desc' }).map(
        (p) => p.id_producto,
      ),
    ).toEqual([1, 2, 3]);
    expect(
      queryProducts(products, { ...query, sort: 'costo', direction: 'asc' }).map(
        (p) => p.id_producto,
      ),
    ).toEqual([3, 1, 2]);
    expect(
      queryProducts(products, { ...query, sort: 'costo', direction: 'desc' }).map(
        (p) => p.id_producto,
      ),
    ).toEqual([1, 3, 2]);
  });

  it('exporta solamente las columnas autorizadas y seleccionadas', () => {
    const csv = catalogCsv(products, [
      { key: 'nombre', label: 'Producto' },
      { key: 'precio', label: 'Precio' },
    ]);
    expect(csv).toContain('"Producto";"Precio"');
    expect(csv).toContain('"Café molido";"22"');
    expect(csv).not.toContain('Costo');
    expect(csv).not.toContain('"14"');
  });

  it('escapa comillas, separadores y formulas en los textos del CSV', () => {
    const csv = catalogCsv(
      [{ ...products[0], nombre: '=SUM(1;2)', codigo_barra: 'COD"12' }],
      [
        { key: 'nombre', label: 'Producto' },
        { key: 'codigo_barra', label: 'Código' },
      ],
    );
    expect(csv).toContain('"\'=SUM(1;2)";"COD""12"');
    expect(csv.startsWith('\uFEFF')).toBe(true);
  });

  it('mantiene accesibles la primera, la actual y la ultima pagina', () => {
    expect(pageNumbers(1, 1)).toEqual([1]);
    expect(pageNumbers(1, 2)).toEqual([1, 2]);
    expect(pageNumbers(10, 20)).toEqual([1, '…', 9, 10, 11, '…', 20]);
    expect(pageNumbers(20, 20)).toEqual([1, '…', 17, 18, 19, 20]);
  });
});
