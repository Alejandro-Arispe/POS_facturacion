import { TestBed } from '@angular/core/testing';
import { ChangeDetectorRef, ElementRef, signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiService } from './api.service';
import { CompanyPermissionsService } from './company-permissions.service';
import { watchReadPermissions } from './watch-read-permissions';
import { StaticReports } from '../../features/companies/sections/reports/static/static';

describe('Lecturas y exportaciones con permisos actuales', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });
  function setup() {
    const response = new Subject<unknown>();
    const api = { getExport: vi.fn(() => response) };
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: api },
        CompanyPermissionsService,
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } },
        },
        { provide: ElementRef, useValue: new ElementRef(document.createElement('div')) },
        { provide: ChangeDetectorRef, useValue: { detectChanges: vi.fn(), markForCheck: vi.fn() } },
      ],
    });
    const permissions = TestBed.inject(CompanyPermissionsService);
    const grant = (codes: string[]) =>
      permissions.savePermissions(
        codes.map(
          (code, id) =>
            ({
              codigo: code,
              codigo_canonico: code,
              activo_rol_permiso: true,
              id_permiso: id,
            }) as never,
        ),
      );
    grant(['report:generate', 'report:export']);
    return { api, response, permissions, grant };
  }
  it('no descarga un reporte en memoria hasta recibir una consulta nueva y autorizada', async () => {
    const { api, response } = setup();
    const view = TestBed.runInInjectionContext(() => new StaticReports()) as any;
    view.salesSummaryReport = { version: 'anterior' };
    view.unwrapStaticReportResponse = (data: unknown) => data;
    view.mapSalesSummaryReport = (data: unknown) => data;
    const downloaded: unknown[] = [];
    view.exportSalesSummaryReportExcel = () => downloaded.push(view.salesSummaryReport);
    const pending = view.exportSalesSummaryReport('excel');
    expect(api.getExport).toHaveBeenCalledWith('/api/reportes/1/resumenventas');
    expect(downloaded).toEqual([]);
    response.next({ version: 'actual' });
    await pending;
    expect(downloaded).toEqual([{ version: 'actual' }]);
  });
  it('un rechazo del servidor retira el reporte anterior y no genera un archivo', async () => {
    const { response } = setup();
    const view = TestBed.runInInjectionContext(() => new StaticReports()) as any;
    const writer = vi.fn();
    view.exportSalesSummaryReportExcel = writer;
    const pending = view.exportSalesSummaryReport('excel');
    response.error({ error: { detail: 'Permiso revocado' } });
    await pending;
    expect(writer).not.toHaveBeenCalled();
    expect(view.salesSummaryError).toBe('Permiso revocado');
  });
  it('descarta lecturas al cambiar la política, sin descartar al refrescar las mismas concesiones', () => {
    const { grant } = setup();
    const discard = vi.fn();
    TestBed.runInInjectionContext(() => watchReadPermissions(discard));
    TestBed.tick();
    grant(['report:generate', 'report:export']);
    TestBed.tick();
    expect(discard).not.toHaveBeenCalled();
    grant(['report:generate']);
    TestBed.tick();
    expect(discard).toHaveBeenCalledOnce();
  });
});
