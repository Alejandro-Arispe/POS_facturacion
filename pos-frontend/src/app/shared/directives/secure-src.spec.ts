import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SecureSrc } from './secure-src';
import { ApiService } from '../../core/services/api.service';
import { CompanyPermissionsService } from '../../core/services/company-permissions.service';
@Component({ imports: [SecureSrc], template: '<img [secureSrc]="path" alt="Producto" />' })
class Host {
  path = '/media/products/a.png';
}

describe('Imagen protegida', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });
  afterEach(() => vi.unstubAllGlobals());
  function setup() {
    const response = new Subject<Blob>();
    const getBlob = vi.fn(() => response);
    const revoke = vi.fn();
    const NativeURL = URL;
    vi.stubGlobal(
      'URL',
      class extends NativeURL {
        static override createObjectURL = vi.fn(() => 'blob:privado');
        static override revokeObjectURL = revoke;
      },
    );
    TestBed.configureTestingModule({
      imports: [Host],
      providers: [CompanyPermissionsService, { provide: ApiService, useValue: { getBlob } }],
    });
    const permissions = TestBed.inject(CompanyPermissionsService);
    const grant = (fields: string[]) =>
      permissions.savePermissions(
        fields.map(
          (code, id) =>
            ({
              id_permiso: id,
              codigo: code,
              codigo_canonico: code,
              activo_rol_permiso: true,
            }) as never,
        ),
      );
    grant(['product:read', 'product:read:imagen']);
    const fixture = TestBed.createComponent(Host);
    return { fixture, response, getBlob, revoke, grant };
  }
  it('obtiene la imagen por la API autenticada y revoca el blob al perder lectura', () => {
    const { fixture, response, getBlob, revoke, grant } = setup();
    fixture.detectChanges();
    expect(getBlob).toHaveBeenCalledWith('/media/products/a.png');
    response.next(new Blob(['imagen']));
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('blob:privado');
    grant(['product:read']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBeNull();
    expect(revoke).toHaveBeenCalledWith('blob:privado');
  });
  it('rechaza referencias externas en lugar de enviarles la sesión', () => {
    const { fixture, getBlob } = setup();
    fixture.componentInstance.path = 'https://example.invalid/imagen.png';
    fixture.detectChanges();
    expect(getBlob).not.toHaveBeenCalled();
  });
});
