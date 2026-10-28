import {
  PermissionByModulePermission,
  PermissionModuleWithPermissions,
} from '../../../../../core/services/company.service';
import { PRODUCT_FIELDS } from '../../../../../core/services/product-fields';
export type CatalogPermission = PermissionByModulePermission;
export interface FieldRow {
  key: string;
  label: string;
  cells: Record<string, CatalogPermission | undefined>;
}
export interface ResourceMatrix {
  key: string;
  label: string;
  module: string;
  general: CatalogPermission[];
  rows: FieldRow[];
  columns: string[];
}
const RESOURCES: Record<string, string> = {
  product: 'Productos',
  role: 'Roles',
  user: 'Personal',
  company: 'Empresa',
  branch: 'Sucursales',
  inventory: 'Existencias',
  inventory_movement: 'Movimientos de inventario',
  inventory_alert: 'Alertas',
  cash_register: 'Cajas',
  cash_movement: 'Movimientos de caja',
  sale: 'Ventas',
  invoice: 'Facturación',
  order: 'Pedidos',
  client: 'Clientes',
  client_category: 'Categorías de clientes',
  report: 'Reportes',
  dashboard: 'Dashboard',
  audit: 'Bitácora',
};
export const ACTIONS: Record<string, string> = {
  read: 'Ver',
  create: 'Crear',
  update: 'Editar',
  delete: 'Eliminar',
  open: 'Abrir', close: 'Cerrar', discount: 'Aplicar descuentos', collect: 'Registrar cobros',
  manage: 'Gestionar', configure: 'Configurar', assign: 'Asignar', void: 'Anular',
};
export function fieldLabel(resource: string, field: string): string {
  if (resource === 'product')
    return (
      PRODUCT_FIELDS.find((f) => f.key === field)?.label.replace(' (Bs)', '') ??
      field.replaceAll('_', ' ')
    );
  return (
    (
      {
        precio_unitario: 'Precio unitario',
        descuento: 'Descuento',
        permissions: 'Permisos',
        nombre: 'Nombre',
        descripcion: 'Descripción',
        activo: 'Estado',
      } as Record<string, string>
    )[field] ?? field.replaceAll('_', ' ')
  );
}
export function buildPermissionMatrix(
  modules: PermissionModuleWithPermissions[],
): ResourceMatrix[] {
  const result = new Map<string, ResourceMatrix>();
  for (const module of modules)
    for (const permission of module.permisos) {
      if (
        permission.activo === false ||
        !permission.codigo_canonico ||
        !permission.recurso ||
        !permission.accion
      )
        continue;
      const resource = permission.recurso;
      let group = result.get(resource);
      if (!group) {
        group = {
          key: resource,
          label: RESOURCES[resource] ?? resource.replaceAll('_', ' '),
          module: module.nombre,
          general: [],
          rows: [],
          columns: ['read', 'create', 'update'],
        };
        result.set(resource, group);
      }
      if (!permission.atributo) group.general.push(permission);
      else {
        let row = group.rows.find((f) => f.key === permission.atributo);
        if (!row) {
          row = {
            key: permission.atributo,
            label: fieldLabel(resource, permission.atributo),
            cells: {},
          };
          group.rows.push(row);
        }
        row.cells[permission.accion] = permission;
        if (!group.columns.includes(permission.accion)) group.columns.push(permission.accion);
      }
    }
  const compare = (a: string, b: string) => a.localeCompare(b, 'es');
  for (const group of result.values()) {
    group.general.sort((a, b) => compare(a.nombre, b.nombre));
    group.rows.sort((a, b) => compare(a.label, b.label));
  }
  return [...result.values()].sort(
    (a, b) => compare(a.module, b.module) || compare(a.label, b.label),
  );
}
export function dependencyWarnings(
  matrix: ResourceMatrix[],
  selected: ReadonlySet<number>,
): string[] {
  const result: string[] = [];
  for (const group of matrix)
    for (const action of group.columns) {
      if (
        !group.rows.some((row) => row.cells[action] && selected.has(row.cells[action]!.id_permiso))
      )
        continue;
      const general = group.general.find((p) => p.accion === action);
      if (!general || !selected.has(general.id_permiso))
        result.push(
          `${group.label}: los campos de ${ACTIONS[action]?.toLowerCase() ?? action} necesitan también el acceso general correspondiente.`,
        );
    }
  const products=matrix.find(group=>group.key==='product');
  const create=products?.general.find(p=>p.accion==='create');
  if(products && create && selected.has(create.id_permiso)) {
    const required=['nombre','id_subcategoria','unidad_medida','precio'];
    const missing=required.filter(field=>{const p=products.rows.find(row=>row.key===field)?.cells['create'];return !p || !selected.has(p.id_permiso);});
    if(missing.length) result.push('Para crear productos también se necesitan permisos de creación en: '+missing.map(field=>fieldLabel('product',field)).join(', ')+'.');
  }
  const sales=matrix.find(group => group.key==='sale');
  const discount=sales?.rows.find(row => row.key==='descuento')?.cells['create'];
  const apply=sales?.general.find(p => p.accion==='discount');
  if(discount && selected.has(discount.id_permiso) && (!apply || !selected.has(apply.id_permiso)))
    result.push('El descuento de venta necesita también la acción Aplicar descuentos.');
  return result;
}
export function filteredMatrix(matrix: ResourceMatrix[], search: string): ResourceMatrix[] {
  const normalize = (s: string) =>
    s
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  const q = normalize(search.trim());
  if (!q) return matrix;
  return matrix
    .map((group) => {
      if (normalize(group.label + ' ' + group.module).includes(q)) return group;
      return {
        ...group,
        general: group.general.filter((p) =>
          normalize(p.nombre + ' ' + p.codigo_canonico).includes(q),
        ),
        rows: group.rows.filter((row) => normalize(row.label + ' ' + row.key).includes(q)),
      };
    })
    .filter((group) => group.general.length || group.rows.length);
}
