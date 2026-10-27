import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ProductFieldEditor } from './product-field-editor';
import { CompanyPermissionsService } from '../../../core/services/company-permissions.service';
describe('Editor de campos', () => {
  beforeEach(() => TestBed.resetTestingModule());
  it('no muestra valores cargados si se revoca su lectura aunque siga pudiendo escribir', () => {
    let read = true;
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CompanyPermissionsService,
          useValue: {
            hasField: (_r: string, action: string, field: string) =>
              field === 'precio' && (action === 'read' ? read : action === 'update'),
          },
        },
      ],
    });
    const editor = TestBed.runInInjectionContext(() => new ProductFieldEditor());
    editor.action = 'update';
    editor.values = { precio: 45 };
    expect(editor.inputValue('precio')).toBe(45);
    read = false;
    expect(editor.visible('precio')).toBe(true);
    expect(editor.inputValue('precio')).toBeUndefined();
    editor.change('precio', 50);
    expect(editor.inputValue('precio')).toBe(50);
    expect(editor.display('precio')).toBe('Restringido');
  });
  it('no emite cambios desde un control sin permiso o mientras se guarda', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: CompanyPermissionsService, useValue: { hasField: () => false } }],
    });
    const editor = TestBed.runInInjectionContext(() => new ProductFieldEditor());
    let emitted = false;
    editor.fieldChange.subscribe(() => (emitted = true));
    editor.change('costo', 99);
    expect(emitted).toBe(false);
  });
});
