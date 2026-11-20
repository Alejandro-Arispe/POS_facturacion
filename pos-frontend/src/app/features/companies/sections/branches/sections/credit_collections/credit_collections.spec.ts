import { TestBed } from '@angular/core/testing';
import { ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { NEVER, of } from 'rxjs';
import { expect, it, vi } from 'vitest';
import { CashRegisterService } from '../../../../../../core/services/cash-register.service';
import { CompanyService, ClientReceivable } from '../../../../../../core/services/company.service';
import { ProductService } from '../../../../../../core/services/product.service';
import { CreditCollections } from './credit_collections';

it('permite abonar con cheque y efectivo después de confirmar el cobro del cheque', () => {
  TestBed.resetTestingModule();
  const registerPayment = vi.fn(() => NEVER);
  const getMetodosPago = vi.fn(() => of([
    { id_metodo_pago: 1, nombre: 'EFECTIVO' },
    { id_metodo_pago: 4, nombre: 'SALDO A FAVOR' },
    { id_metodo_pago: 6, nombre: 'CHEQUE' },
  ]));
  TestBed.configureTestingModule({ providers: [
    { provide: ActivatedRoute, useValue: { snapshot: {
      paramMap: convertToParamMap({ id: '1' }), queryParamMap: convertToParamMap({ sessionId: '4' }),
    } } },
    { provide: ChangeDetectorRef, useValue: { detectChanges: vi.fn() } },
    { provide: CompanyService, useValue: { getClientesEmpresa: () => of([]) } },
    { provide: ProductService, useValue: { getMetodosPago } },
    { provide: CashRegisterService, useValue: { registrarPagoCreditoSesionCaja: registerPayment } },
  ] });
  const screen = TestBed.runInInjectionContext(() => new CreditCollections());
  screen.ngOnInit();
  expect(getMetodosPago).toHaveBeenCalledWith(true);
  expect(screen['paymentMethods'].map((method) => method.id)).toEqual([1, 6]);

  screen['openPaymentTab']({ id_cxc: 11, saldo_pendiente: 100 } as ClientReceivable);
  const cash = screen['paymentRows'][0];
  screen['updatePaymentAmount'](cash, 20);
  screen['addPaymentRow']();
  const cheque = screen['paymentRows'][1];
  expect(cheque.paymentMethodId).toBe(6);
  screen['updatePaymentAmount'](cheque, 30);
  Object.assign(cheque.cheque!, { banco: 'Banco de prueba', numero: '000123', fecha_emision: '2026-11-18' });
  screen['registerCreditPayment']();
  expect(registerPayment).not.toHaveBeenCalled();
  expect(screen['remainingPaymentAmount']).toBe(50);

  cheque.cheque!.cobrado = true;
  screen['registerCreditPayment']();
  expect(registerPayment).toHaveBeenCalledWith('4', {
    id_cxc: 11,
    pagos_credito: [
      { id_metodo_pago: 1, monto_pagado: '20.00' },
      { id_metodo_pago: 6, monto_pagado: '30.00', cheque: cheque.cheque },
    ],
  });
});
