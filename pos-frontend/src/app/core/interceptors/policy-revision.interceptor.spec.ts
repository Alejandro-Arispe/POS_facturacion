import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CompanyPermissionsService } from '../services/company-permissions.service';
import { PolicyRevisionService } from '../services/policy-revision.service';
import { policyRevisionInterceptor } from './policy-revision.interceptor';

describe('Revocación mientras hay solicitudes pendientes', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let permissions: CompanyPermissionsService;
  let policy: PolicyRevisionService;
  const grant = (codes: string[]) => permissions.savePermissions(codes.map((code, id) => ({
    id_permiso: id, codigo: code, codigo_canonico: code, activo_rol_permiso: true,
  }) as never));

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([policyRevisionInterceptor])), provideHttpClientTesting(),
    ] });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    permissions = TestBed.inject(CompanyPermissionsService);
    policy = TestBed.inject(PolicyRevisionService);
    grant(['product:read', 'product:read:costo']);
  });
  afterEach(() => { backend.verify(); TestBed.resetTestingModule(); localStorage.clear(); });

  it('una respuesta antigua con costo no llega al componente después de revocar', () => {
    const next = vi.fn(), error = vi.fn();
    http.get('/api/productos').subscribe({ next, error });
    grant(['product:read']);
    backend.expectOne('/api/productos').flush([{ costo: 25 }]);
    expect(next).not.toHaveBeenCalled();
    expect(error.mock.calls[0][0].status).toBe(409);
    const current = vi.fn();
    http.get('/api/productos').subscribe(current);
    backend.expectOne('/api/productos').flush([{ nombre: 'Actual' }]);
    expect(current).toHaveBeenCalledWith([{ nombre: 'Actual' }]);
  });
  it('refrescar las mismas concesiones no descarta respuestas válidas', () => {
    const next = vi.fn();
    http.get('/api/productos').subscribe(next);
    grant(['product:read:costo', 'product:read']);
    backend.expectOne('/api/productos').flush([{ costo: 25 }]);
    expect(next).toHaveBeenCalledWith([{ costo: 25 }]);
  });
  it('cambiar de empresa descarta la respuesta aunque los permisos coincidan', () => {
    policy.update('scope', 'empresa:1');
    const next = vi.fn(), error = vi.fn();
    http.get('/api/reportes/1/resumenventas').subscribe({ next, error });
    policy.update('scope', 'empresa:2');
    backend.expectOne('/api/reportes/1/resumenventas').flush({ total: 99 });
    expect(next).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledOnce();
  });
  it('al cerrar sesión también invalida imágenes o descargas pendientes', () => {
    const next = vi.fn(), error = vi.fn();
    http.get('/media/products/product.png', { responseType: 'blob' }).subscribe({ next, error });
    permissions.clearPermissions();
    backend.expectOne('/media/products/product.png').flush(new Blob(['image']));
    expect(next).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledOnce();
  });
  it('no reintenta una escritura que pudo haber sido confirmada', () => {
    const next = vi.fn(), error = vi.fn();
    http.put('/api/roles/7', { activo: false }).subscribe({ next, error });
    grant([]);
    backend.expectOne('/api/roles/7').flush({ activo: false });
    expect(next).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledOnce();
    backend.expectNone('/api/roles/7');
  });
  it('la consulta de contexto puede establecer permisos nuevos', () => {
    const next = vi.fn();
    http.get('/api/empresas/1/mis-permisos').subscribe(next);
    grant([]);
    backend.expectOne('/api/empresas/1/mis-permisos').flush({ permisos: [] });
    expect(next).toHaveBeenCalledWith({ permisos: [] });
  });
});
