import { TestBed } from '@angular/core/testing';
import { ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Sales } from './sales';
import { CompanyPermissionsService } from '../../../../../../core/services/company-permissions.service';
import { CompanyService } from '../../../../../../core/services/company.service';
import { ApiService } from '../../../../../../core/services/api.service';
import { ProductService } from '../../../../../../core/services/product.service';
import { CashRegisterService } from '../../../../../../core/services/cash-register.service';
import { OrderService } from '../../../../../../core/services/order.service';

describe('POS con permisos por acción', () => {
  beforeEach(() => { TestBed.resetTestingModule(); localStorage.clear(); });
  function setup(codes: string[]) {
    const cash={crearMovimientoCajaSesion:vi.fn(), getVentasSesionCaja:vi.fn(() => of([]))};
    TestBed.configureTestingModule({providers:[
      {provide:ActivatedRoute,useValue:{snapshot:{paramMap:convertToParamMap({id:'1',branchId:'2',cashRegisterId:'3'}),queryParamMap:convertToParamMap({sessionId:'4'})}}},
      {provide:Router,useValue:{navigate:vi.fn()}},
      {provide:ChangeDetectorRef,useValue:{detectChanges:vi.fn(),markForCheck:vi.fn()}},
      {provide:ApiService,useValue:{}}, {provide:CompanyService,useValue:{}},
      {provide:ProductService,useValue:{}}, {provide:CashRegisterService,useValue:cash},
      {provide:OrderService,useValue:{}}, CompanyPermissionsService,
    ]});
    const permissions=TestBed.inject(CompanyPermissionsService);
    function grant(next:string[]) { permissions.savePermissions(next.map((code,id) => ({codigo:code,codigo_canonico:code,activo_rol_permiso:true,id_permiso:id} as never))); }
    grant(codes);
    const view=TestBed.runInInjectionContext(() => new Sales()) as any;
    return {view,permissions,cash,grant};
  }
  it('retira los descuentos de un carrito abierto al revocar cualquiera de sus permisos', () => {
    const {view,grant}=setup(['sale:create','sale:discount','sale:create:descuento']);
    view.saleDetails=[{idProducto:1,precio:20,cantidad:2,descuento:5,descuentoTipo:'fixed'}];
    expect(view.discountTotal).toBe(5);
    for(const codes of [['sale:create','sale:discount'],['sale:create','sale:create:descuento']]) {
      grant(codes); expect(view.canDiscount).toBe(false); expect(view.discountTotal).toBe(0);
      expect(view.buildSaleDetailPayload()[0].descuento).toBe(0);
    }
  });
  it('un permiso de inventario no habilita un movimiento manual de caja', () => {
    const {view,cash}=setup(['inventory_movement:create']);
    view.quickMovementMonto=5;view.quickMovementConcepto='Ingreso';
    view.confirmarMovimientoRapido(new Event('submit'));
    expect(cash.crearMovimientoCajaSesion).not.toHaveBeenCalled();
    expect(view.quickMovementError).toContain('permiso');
  });
  it('no consulta el historial sin lectura y retira el historial al revocarla', () => {
    const {view,cash,grant}=setup(['sale:create']);
    view.loadSalesHistory();expect(cash.getVentasSesionCaja).not.toHaveBeenCalled();
    grant(['sale:read']);view.loadSalesHistory();expect(cash.getVentasSesionCaja).toHaveBeenCalledOnce();
    view.salesHistory=[{id:9}];grant(['sale:create']);view.loadSalesHistory();expect(view.salesHistory).toEqual([]);
    expect(cash.getVentasSesionCaja).toHaveBeenCalledOnce();
  });  it('restaura el precio de catálogo del carrito si se revoca el permiso de precio especial', () => {
    const {view,grant}=setup(['sale:create','sale:create:precio_unitario']);
    const item={idProducto:1,precio:20,precioCatalogo:20,cantidad:2,descuento:0,descuentoTipo:'fixed'};
    view.saleDetails=[item];view.updateItemPrice(item,18);
    expect(view.total).toBe(36);expect(view.buildSaleDetailPayload()[0].precio_unitario).toBe(18);
    grant(['sale:create']);expect(view.total).toBe(40);expect(view.buildSaleDetailPayload()[0].precio_unitario).toBe(20);
    view.updateItemPrice(item,1);expect(view.total).toBe(40);
  });

});
