import { expect, test } from '@playwright/test';
import { API_BASE, DEMO_ADMIN, apiLogin, gotoCompanyPath, loginAsAdmin, sessionToken } from './helpers';

test.describe('Facturación SIAT', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('historial de facturas muestra estados VALIDADA', async ({ page }) => {
    await gotoCompanyPath(page, 'reports/invoices');
    await expect(page.getByRole('heading', { name: /^facturas$/i })).toBeVisible();
    await expect(page.getByText(/VALIDADA/i).first()).toBeVisible();
    await expect(page.getByRole('link', { name: /abrir xml/i }).first()).toBeVisible();
  });

  test('descarga XML de factura responde application/xml', async ({ page, request }) => {
    await gotoCompanyPath(page, 'reports/invoices');
    await expect(page.getByText(/VALIDADA/i).first()).toBeVisible();

    const token = await sessionToken(page);
    const list = await request.get(`${API_BASE}/api/empresas/1/facturas`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(list.ok()).toBeTruthy();
    const invoices = (await list.json()) as Array<{ id_factura: number }>;
    expect(invoices.length).toBeGreaterThan(0);

    const xml = await request.get(`${API_BASE}/api/facturas/${invoices[0].id_factura}/xml`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(xml.ok()).toBeTruthy();
    expect(xml.headers()['content-type'] ?? '').toMatch(/xml/i);
    expect(await xml.text()).toContain('facturaComputarizadaCompraVenta');
  });

  test('descarga PDF de factura responde application/pdf', async ({ request }) => {
    const token = await apiLogin(request, DEMO_ADMIN.email, DEMO_ADMIN.password);
    const list = await request.get(`${API_BASE}/api/empresas/1/facturas`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const invoices = (await list.json()) as Array<{ id_factura: number }>;
    const pdf = await request.get(`${API_BASE}/api/facturas/${invoices[0].id_factura}/pdf`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(pdf.ok()).toBeTruthy();
    expect(pdf.headers()['content-type'] ?? '').toMatch(/pdf/i);
    expect((await pdf.body()).slice(0, 4).toString('utf8')).toBe('%PDF');
  });

  test('cajero no puede emitir factura (invoice:issue)', async ({ request }) => {
    const token = await apiLogin(request, 'cajero.centro@example.com', 'Tienda2026!');
    // Abrir sesión limpia
    const cajas = await request.get(`${API_BASE}/api/empresas/1/sucursales/1/cajas`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const caja = ((await cajas.json()) as Array<{ id_caja: number; sesion_activa?: { id_caja_sesion: number; es_usuario_actual?: boolean } }>)[0];
    if (caja.sesion_activa?.es_usuario_actual) {
      await request.post(`${API_BASE}/api/cajas/sesiones/${caja.sesion_activa.id_caja_sesion}/cierres`, {
        headers: { Authorization: `Bearer ${token}` },
        data: [{ id_metodo_pago: 1, monto_esperado: 50, monto_real: 50, diferencia: 0, observacion: 'x' }],
      });
    }
    const open = await request.post(`${API_BASE}/api/cajas/${caja.id_caja}/sesiones`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { monto_inicial: 50, nota: 'factura-deny' },
    });
    const sid = ((await open.json()) as { id_caja_sesion: number }).id_caja_sesion;
    const venta = await request.post(`${API_BASE}/api/ventas/sesiones/${sid}/ventas`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        id_tipo_venta: 1,
        id_cliente: 1,
        id_metodo_pago: 1,
        factura_linea: true,
        codigo_tipo_documento: 1,
        subtotal: 16.5,
        descuento_total: 0,
        total: 16.5,
        estado: 'pagada',
        pagos: [{ id_metodo_pago: 1, monto: 16.5 }],
        detalles: [{ id_producto: 3, cantidad: 1, precio_unitario: 16.5, descuento: 0, subtotal: 16.5 }],
      },
    });
    expect(venta.ok()).toBeFalsy();
    expect([400, 403]).toContain(venta.status());
    // limpiar sesión
    await request.post(`${API_BASE}/api/cajas/sesiones/${sid}/cierres`, {
      headers: { Authorization: `Bearer ${token}` },
      data: [{ id_metodo_pago: 1, monto_esperado: 50, monto_real: 50, diferencia: 0, observacion: 'x' }],
    });
  });
});
