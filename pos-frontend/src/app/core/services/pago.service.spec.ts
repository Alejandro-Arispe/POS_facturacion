import { describe, expect, it, vi } from 'vitest';
import { firstValueFrom, of } from 'rxjs';
import { PagoService } from './pago.service';

describe('Compatibilidad del catálogo de planes', () => {
  it('envía el origen del frontend y permite pagar sin empresa', () => {
    const api = { post: vi.fn(() => of({})) };
    const service = new PagoService(api as never);
    service.crearCheckout({ id_plan: 2 });
    expect(api.post).toHaveBeenCalledWith('/api/pagos/checkout', {
      id_plan: 2,
      frontend_origin: window.location.origin,
    });
  });
  it('impide elegir planes de un backend antiguo que todavía cobra en dólares', async () => {
    const service = new PagoService({ get: () => of([{ id_plan: 1, precio: '29.99' }]) } as never);
    await expect(firstValueFrom(service.getPlanes())).rejects.toMatchObject({
      error: { detail: expect.stringContaining('Reinicia el backend') },
    });
  });

  it('conserva los límites y precios enviados por el servidor en BOB', async () => {
    const plans = [{ id_plan: 1, precio: '0.00', moneda: 'BOB', limites: { max_usuarios: 2 } }];
    const service = new PagoService({ get: () => of(plans) } as never);
    expect(await firstValueFrom(service.getPlanes())).toEqual(plans);
  });
});
