import { afterEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { PaymentResultComponent } from './payment-result';

function setup(status = 'pendiente', url = '/payment/success?session_id=cs_test_demo') {
  const pago = {
    obtenerSesionStripe: vi.fn(() => 'cs_test_demo'),
    confirmarPago: vi.fn(() =>
      of({
        suscripcion: {
          estado: status,
          id_empresa: 8,
          id_plan: 1,
          fecha_fin: null,
          plan_nombre: 'Gratuito',
        },
      }),
    ),
    limpiarSesionStripe: vi.fn(),
  };
  const onboarding = { company: null, complete: vi.fn() };
  const component = new PaymentResultComponent(
    { snapshot: { queryParamMap: { get: () => null } } } as never,
    { url, navigate: vi.fn() } as never,
    pago as never,
    onboarding as never,
    {} as never,
    {} as never,
    { markForCheck: vi.fn() } as never,
  );
  const view = component as unknown as {
    estado: string;
    suscripcion: { fecha_fin: string | null } | null;
  };
  return { component, view, pago, onboarding };
}

describe('Confirmación del plan', () => {
  afterEach(() => vi.useRealTimers());

  it('un HTTP exitoso con pago pendiente nunca concede acceso ni borra la sesión', () => {
    vi.useFakeTimers();
    const { component, view, pago, onboarding } = setup();
    component.ngOnInit();
    expect(view.estado).toBe('cargando');
    vi.runAllTimers();
    expect(view.estado).toBe('pendiente');
    expect(pago.confirmarPago).toHaveBeenCalledTimes(6);
    expect(pago.limpiarSesionStripe).not.toHaveBeenCalled();
    expect(onboarding.complete).not.toHaveBeenCalled();
    component.ngOnDestroy();
  });

  it('activa el acceso gratuito sin inventar una fecha de vencimiento', () => {
    const { component, view, onboarding } = setup('activo');
    component.ngOnInit();
    expect(view.estado).toBe('exito');
    expect(view.suscripcion?.fecha_fin).toBeNull();
    expect(onboarding.complete).toHaveBeenCalledOnce();
  });

  it('cancelar no confirma ni activa un pago', () => {
    const { component, view, pago } = setup('activo', '/payment/cancel');
    component.ngOnInit();
    expect(view.estado).toBe('cancelado');
    expect(pago.confirmarPago).not.toHaveBeenCalled();
  });

  it('reconoce un plan pagado aunque la empresa todavía no exista', () => {
    const { component, view, pago, onboarding } = setup();
    pago.confirmarPago.mockReturnValue(
      of({
        compra: {
          id_compra_plan: 12,
          id_plan: 2,
          plan_nombre: 'Básico',
          estado: 'pagado',
          gratuito: false,
          id_empresa: null,
        },
      }) as never,
    );
    component.ngOnInit();
    expect(view.estado).toBe('exito');
    expect((component as unknown as { idEmpresa: number | null }).idEmpresa).toBeNull();
    expect(onboarding.complete).toHaveBeenCalledOnce();
  });

  it('deja el pago recuperable si falla la consulta y cancela el sondeo al salir', () => {
    vi.useFakeTimers();
    const { component, view, pago } = setup();
    pago.confirmarPago.mockReturnValue(
      throwError(() => ({ error: { detail: 'Sesión ajena' } })) as never,
    );
    component.ngOnInit();
    expect(view.estado).toBe('error');
    expect(pago.limpiarSesionStripe).not.toHaveBeenCalled();
    component.ngOnDestroy();
    vi.runAllTimers();
    expect(pago.confirmarPago).toHaveBeenCalledOnce();
  });
});
