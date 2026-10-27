import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ApiService } from './api.service';
import { CompanyPermission, CompanyPermissionsService } from './company-permissions.service';

function permission(code: string, active = true, canonical = code): CompanyPermission {
  return {
    id_permiso: 1,
    codigo: code,
    codigo_canonico: canonical,
    nombre: code,
    id_modulo: 1,
    modulo: { id_modulo: 1, codigo: 'INVENTARIO', nombre: 'Inventario' },
    activo_rol_permiso: active,
  };
}

describe('Contexto de permisos', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
  });
  function service(api = { get: () => of({}) }) {
    TestBed.configureTestingModule({
      providers: [CompanyPermissionsService, { provide: ApiService, useValue: api }],
    });
    return TestBed.inject(CompanyPermissionsService);
  }
  it('deniega sin contexto y descarta permisos de una sesion anterior', () => {
    localStorage.setItem('company_permissions_by_code', JSON.stringify({ ROL_EDITAR: true }));
    const p = service();
    expect(p.hasPermission('role:update')).toBe(false);
    expect(p.getPermissionCodes()).toEqual([]);
    expect(p.camposProducto().costo).toBe('OCULTO');
  });
  it('un catalogo vacio o todo falso mantiene la denegacion', () => {
    const p = service();
    p.savePermissions([]);
    expect(p.getPermissionCodes()).toEqual([]);
    p.savePermissions([permission('role:update', false, 'role:update')]);
    expect(p.hasPermission('role:update')).toBe(false);
    expect(p.hasPermission('role:update')).toBe(false);
  });
  it('combina concesiones activas usando únicamente el catálogo canónico', () => {
    const p = service();
    p.savePermissions([
      permission('product:read', true, 'product:read'),
      permission('product:read', false, 'product:read'),
      permission('role:update:permissions'),
    ]);
    expect(p.hasPermission('product:read')).toBe(true);
    expect(p.hasPermission('PRODUCTO_VER')).toBe(false);
    expect(p.hasPermission('role:update:permissions')).toBe(true);
    expect(p.hasPermission('role:update')).toBe(false);
  });
  it('cambiar contexto elimina permisos y campos anteriores', () => {
    const p = service();
    p.savePermissions([permission('role:update', true, 'role:update')]);
    p.clearPermissions();
    expect(p.getPermissionCodes()).toEqual([]);
    expect(p.camposProducto().precio).toBe('OCULTO');
    expect(localStorage.getItem('company_permissions_by_code')).toBeNull();
  });
  it('lectura y escritura de atributos requieren su accion general y son independientes', () => {
    const p = service();
    p.savePermissions([
      permission('product:update'),
      permission('product:update:precio'),
      permission('product:read:costo'),
    ]);
    expect(p.hasField('product', 'update', 'precio')).toBe(true);
    expect(p.hasField('product', 'read', 'precio')).toBe(false);
    expect(p.hasField('product', 'read', 'costo')).toBe(false);
  });
  it('actualiza campos y delegacion con la sesion abierta sin borrar almacenamiento manualmente', () => {
    vi.useFakeTimers();
    const get = vi.fn(() =>
      of({
        permisos: [
          { ...permission('product:read'), id_permiso: 1 },
          { ...permission('product:read:precio'), id_permiso: 2, puede_delegar: true },
        ],
        suscripcion_activa: { estado: 'activo', plan: {} },
      }),
    );
    const p = service({ get });
    p.watchContext('4', '2');
    vi.advanceTimersByTime(30000);
    expect(get).toHaveBeenCalledWith('/api/empresas/4/mis-permisos?id_sucursal=2');
    expect(p.hasField('product', 'read', 'precio')).toBe(true);
    expect(p.canDelegate(2)).toBe(true);
    get.mockReturnValue(of({ permisos: [], suscripcion_activa: { estado: 'activo', plan: {} } }));
    window.dispatchEvent(new Event('focus'));
    expect(p.hasField('product', 'read', 'precio')).toBe(false);
    expect(p.canDelegate(2)).toBe(false);
    p.clearPermissions();
    const count = get.mock.calls.length;
    vi.advanceTimersByTime(60000);
    expect(get).toHaveBeenCalledTimes(count);
  });
  it('un fallo de refresco limpia capacidades y nunca habilita un respaldo permisivo', () => {
    vi.useFakeTimers();
    const get = vi.fn(() => throwError(() => new Error('403')));
    const p = service({ get });
    p.savePermissions([permission('product:read'), permission('product:read:costo')]);
    p.watchContext(4);
    vi.advanceTimersByTime(30000);
    expect(p.getPermissionCodes()).toEqual([]);
    expect(p.contextError()).toBe(true);
  });
});
