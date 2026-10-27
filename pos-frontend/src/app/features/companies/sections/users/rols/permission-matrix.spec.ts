import { describe, expect, it } from 'vitest';
import { buildPermissionMatrix, dependencyWarnings, filteredMatrix } from './permission-matrix';
import {
  PermissionByModulePermission,
  PermissionModuleWithPermissions,
} from '../../../../../core/services/company.service';
function permission(id: number, code: string): PermissionByModulePermission {
  const [recurso, accion, atributo] = code.split(':');
  return {
    id_permiso: id,
    codigo: code,
    codigo_canonico: code,
    recurso,
    accion,
    atributo: atributo ?? null,
    activo: true,
    nombre: code,
    descripcion: code,
  };
}
const catalog: PermissionModuleWithPermissions[] = [
  {
    id_modulo: 1,
    codigo: 'INVENTARIO',
    nombre: 'Inventario',
    permisos: [
      permission(1, 'product:read'),
      permission(2, 'product:update'),
      permission(3, 'product:read:costo'),
      permission(4, 'product:update:precio'),
      permission(5, 'inventory:read'),
    ],
  },
];
describe('Matriz del catalogo', () => {
  it('separa recursos y no inventa acciones ausentes para completar celdas', () => {
    const matrix = buildPermissionMatrix(catalog);
    const product = matrix.find((g) => g.key === 'product')!;
    expect(matrix).toHaveLength(2);
    expect(product.general.map((p) => p.id_permiso)).toEqual([1, 2]);
    const cost = product.rows.find((row) => row.key === 'costo')!;
    expect(cost.cells['read']?.id_permiso).toBe(3);
    expect(cost.cells['create']).toBeUndefined();
    expect(cost.cells['update']).toBeUndefined();
  });
  it('advierte dependencias sin modificar la seleccion ni conceder lectura o escritura', () => {
    const selected = new Set([4]);
    expect(dependencyWarnings(buildPermissionMatrix(catalog), selected)).toHaveLength(1);
    expect([...selected]).toEqual([4]);
    expect(dependencyWarnings(buildPermissionMatrix(catalog), new Set([2, 4]))).toEqual([]);
  });
  it('buscar no borra permisos fuera del resultado y admite tildes', () => {
    const matrix = buildPermissionMatrix(catalog);
    expect(filteredMatrix(matrix, 'precio')[0].rows.map((r) => r.key)).toEqual(['precio']);
    expect(matrix.find((g) => g.key === 'product')!.rows).toHaveLength(2);
    expect(filteredMatrix(matrix, 'costo')[0].rows[0].cells['read']?.id_permiso).toBe(3);
  });
  it('incorpora campos de nuevos recursos desde metadatos y omite inactivos', () => {
    const extra = {
      ...catalog[0],
      permisos: [
        permission(9, 'shipment:update:referencia'),
        { ...permission(10, 'shipment:read:secreto'), activo: false },
      ],
    };
    const matrix = buildPermissionMatrix([extra]);
    expect(matrix[0].rows[0].key).toBe('referencia');
    expect(matrix[0].rows).toHaveLength(1);
  });
  it('advierte los campos obligatorios de creacion sin seleccionarlos automaticamente',()=>{
    const extra={...catalog[0],permisos:[permission(20,'product:create'),permission(21,'product:create:nombre')]};
    const selected=new Set([20]);expect(dependencyWarnings(buildPermissionMatrix([extra]),selected).join(' ')).toContain('Precio de venta');expect([...selected]).toEqual([20]);
  });

});
