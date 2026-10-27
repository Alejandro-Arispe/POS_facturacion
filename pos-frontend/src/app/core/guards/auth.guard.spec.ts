import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { CompanyAccessResponse, CompanyService } from '../services/company.service';
import { CompanyPermissionsService } from '../services/company-permissions.service';
import { ApiService } from '../services/api.service';

describe('Navegacion con contexto RBAC', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });
  function setup(fromSettings = false) {
    const response = new Subject<CompanyAccessResponse>();
    const getMisPermisos = vi.fn(() => response);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => true } },
        { provide: CompanyService, useValue: { getMisPermisos } },
        { provide: ApiService, useValue: {} },
        CompanyPermissionsService,
      ],
    });
    const permissions = TestBed.inject(CompanyPermissionsService);
    const route = {
      paramMap: convertToParamMap(fromSettings ? {} : { id: '1', branchId: '2' }),
      queryParamMap: convertToParamMap(fromSettings ? { companyId: '1' } : {}),
      data: { permission: 'role:read' },
    } as unknown as ActivatedRouteSnapshot;
    const state = { url: '/company/1/users/rols' } as RouterStateSnapshot;
    const guard = TestBed.runInInjectionContext(() =>
      authGuard(route, state),
    ) as Observable<unknown>;
    return { response, getMisPermisos, permissions, guard };
  }
  const active = { estado: 'activo', plan: {} };
  it('carga tambien el contexto al abrir configuracion por enlace directo', () => {
    const { guard, getMisPermisos } = setup(true);
    const subscription = guard.subscribe();
    expect(getMisPermisos).toHaveBeenCalledWith('1', null);
    subscription.unsubscribe();
  });
  it('espera la respuesta del servidor antes de evaluar el permiso de ruta', () => {
    const { response, guard, permissions, getMisPermisos } = setup();
    const results: unknown[] = [];
    guard.subscribe((value) => results.push(value));
    expect(results).toEqual([]);
    expect(permissions.hasPermission('role:read')).toBe(false);
    expect(getMisPermisos).toHaveBeenCalledWith('1', '2');
    response.next({
      suscripcion_activa: active,
      permisos: [{ codigo: 'role:read', codigo_canonico: 'role:read', activo_rol_permiso: true }],
    } as CompanyAccessResponse);
    expect(results).toEqual([true]);
  });
  it('redirige si el nuevo contexto carece del permiso y limpia ante un error', () => {
    const { response, guard, permissions } = setup();
    const results: unknown[] = [];
    guard.subscribe((value) => results.push(value));
    response.next({ suscripcion_activa: active, permisos: [] } as unknown as CompanyAccessResponse);
    expect(TestBed.inject(Router).serializeUrl(results[0] as never)).toBe('/company/1/branches');
    response.error(new Error('403'));
    expect(permissions.getPermissionCodes()).toEqual([]);
    expect(TestBed.inject(Router).serializeUrl(results[1] as never)).toBe('/my-companies');
  });
});
