import { describe, expect, it, vi } from 'vitest';
import { NEVER } from 'rxjs';
import { Checkout } from './checkout';

describe('Checkout directo', () => {
  it('inicia el pago al entrar sin pedir ni crear una empresa', () => {
    const pago = { crearCheckout: vi.fn(() => NEVER) };
    const flow = { selectPlan: () => 2, plan: 2 };
    const router = { navigate: vi.fn() };
    const component = new Checkout(
      pago as never,
      flow as never,
      { snapshot: { queryParamMap: { get: () => '2' } } } as never,
      router as never,
      { markForCheck: vi.fn() } as never,
    );
    component.ngOnInit();
    component.ngOnInit();
    expect(pago.crearCheckout).toHaveBeenCalledOnce();
    expect(pago.crearCheckout).toHaveBeenCalledWith({ id_plan: 2 });
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
