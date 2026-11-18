import { expect, test } from '@playwright/test';
import { API_BASE, DEMO_CASHIER, apiLogin } from './helpers';

test.describe('Caja y venta (API)', () => {
  test('cajero puede abrir caja, vender y cerrar', async ({ request }) => {
    const token = await apiLogin(request, DEMO_CASHIER.email, DEMO_CASHIER.password);

    const cajas = await request.get(`${API_BASE}/api/empresas/1/sucursales/1/cajas`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(cajas.ok()).toBeTruthy();
    const cajaList = (await cajas.json()) as Array<{
      id_caja: number;
      sesion_activa?: { id_caja_sesion: number; es_usuario_actual?: boolean } | null;
    }>;
    expect(cajaList.length).toBeGreaterThan(0);
    const caja = cajaList[0];

    if (caja.sesion_activa?.es_usuario_actual) {
      const sid = caja.sesion_activa.id_caja_sesion;
      const close = await request.post(`${API_BASE}/api/cajas/sesiones/${sid}/cierres`, {
        headers: { Authorization: `Bearer ${token}` },
        data: [
          {
            id_metodo_pago: 1,
            monto_esperado: 100,
            monto_real: 100,
            diferencia: 0,
            observacion: 'cleanup e2e',
          },
        ],
      });
      expect(close.ok(), await close.text()).toBeTruthy();
    }

    const open = await request.post(`${API_BASE}/api/cajas/${caja.id_caja}/sesiones`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { monto_inicial: 100, nota: 'e2e playwright' },
    });
    expect(open.ok(), await open.text()).toBeTruthy();
    const sesion = (await open.json()) as { id_caja_sesion: number };
    const sid = sesion.id_caja_sesion;

    const productos = await request.get(`${API_BASE}/api/empresas/1/productos`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const prodList = (await productos.json()) as Array<{ id_producto: number; precio: number }>;
    const prod = prodList.find((p) => Number(p.precio) > 0) ?? prodList[0];
    const precio = Number(prod.precio);

    const venta = await request.post(`${API_BASE}/api/ventas/sesiones/${sid}/ventas`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        id_tipo_venta: 1,
        id_metodo_pago: 1,
        factura_linea: false,
        subtotal: precio,
        descuento_total: 0,
        total: precio,
        estado: 'pagada',
        pagos: [{ id_metodo_pago: 1, monto: precio }],
        detalles: [
          {
            id_producto: prod.id_producto,
            cantidad: 1,
            precio_unitario: precio,
            descuento: 0,
            subtotal: precio,
          },
        ],
      },
    });
    expect(venta.ok(), await venta.text()).toBeTruthy();
    const ventaBody = (await venta.json()) as { id_venta: number };
    expect(ventaBody.id_venta).toBeTruthy();

    const hist = await request.get(`${API_BASE}/api/ventas/sesiones/${sid}/ventas`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(hist.ok()).toBeTruthy();
    expect(((await hist.json()) as unknown[]).length).toBeGreaterThan(0);

    const close = await request.post(`${API_BASE}/api/cajas/sesiones/${sid}/cierres`, {
      headers: { Authorization: `Bearer ${token}` },
      data: [
        {
          id_metodo_pago: 1,
          monto_esperado: 100 + precio,
          monto_real: 100 + precio,
          diferencia: 0,
          observacion: 'e2e cierre',
        },
      ],
    });
    expect(close.ok(), await close.text()).toBeTruthy();
  });

  test('abrir caja sin token falla', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/cajas/1/sesiones`, {
      data: { monto_inicial: 10, nota: 'no-auth' },
    });
    expect([401, 403]).toContain(res.status());
  });
});
