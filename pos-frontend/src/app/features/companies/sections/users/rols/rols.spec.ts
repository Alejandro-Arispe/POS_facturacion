import { TestBed } from '@angular/core/testing';
import { ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Rols } from './rols';
import { CompanyService } from '../../../../../core/services/company.service';
import { CompanyPermissionsService } from '../../../../../core/services/company-permissions.service';

describe('Administracion de roles', () => {
  beforeEach(() => TestBed.resetTestingModule());
  function setup() {
    const p = {
      id_permiso: 1,
      codigo: 'product:read',
      codigo_canonico: 'product:read',
      recurso: 'product',
      accion: 'read',
      atributo: null,
      nombre: 'Ver productos',
      activo: true,
      descripcion: '',
    };
    const api = {
      getRoles: vi.fn(() => of([])),
      getPermisosPorModulo: vi.fn(() =>
        of([{ id_modulo: 1, codigo: 'PRODUCTOS', nombre: 'Productos', permisos: [p] }]),
      ),
      getDelegables: vi.fn(() => of([1])),
      getRoleCapabilities: vi.fn(() => of({ crear: true, editar: true })),
      crearRol: vi.fn(() => of({ rol: { id_rol: 4 } })),
      actualizarRol: vi.fn(() => of({})),
      getRoleById: vi.fn(() => throwError(() => new Error('Fallo de carga'))),
      getMisPermisos: vi.fn(() => of({ permisos: [], suscripcion_activa: null })),
    };
    const permissions = {
      hasPermission: vi.fn(() => true),
      canDelegate: vi.fn((id: number) => id === 1),
      clearPermissions: vi.fn(),
      savePermissions: vi.fn(),
      saveActiveSubscription: vi.fn(),
      watchContext: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '2' }) } },
        },
        { provide: CompanyService, useValue: api },
        { provide: CompanyPermissionsService, useValue: permissions },
        { provide: ChangeDetectorRef, useValue: { detectChanges: vi.fn() } },
      ],
    });
    const component = TestBed.runInInjectionContext(() => new Rols());
    component.ngOnInit();
    const view = component as any;
    return { view, api, permissions, p };
  }
  it('guarda metadatos y concesiones en una sola peticion y bloquea ids no administrables', () => {
    const { view, api, p } = setup();
    view.create();
    view.form = { nombre: ' Cajero ', descripcion: 'Atiende la caja', activo: true };
    view.toggle(p, true);
    view.toggleDelegation(p, true);
    view.toggle({ ...p, id_permiso: 99 }, true);
    view.save(new Event('submit'));
    expect(api.crearRol).toHaveBeenCalledTimes(1);
    expect(api.crearRol).toHaveBeenCalledWith('2', {
      nombre: 'Cajero',
      descripcion: 'Atiende la caja',
      activo: true,
      permiso_ids: [1],
      delegable_ids: [1],
    });
    expect(api.actualizarRol).not.toHaveBeenCalled();
  });
  it('un error al cargar el detalle nunca permite sobrescribirlo con permisos vacios', () => {
    const { view, api } = setup();
    view.open({ id_rol: 4, nombre: 'Cajero', tipo: 'CUSTOM', activo: true, administrable: true });
    view.form.nombre = 'Cambio';
    view.save(new Event('submit'));
    expect(api.actualizarRol).not.toHaveBeenCalled();
    expect(view.readOnly).toBe(true);
  });
});
