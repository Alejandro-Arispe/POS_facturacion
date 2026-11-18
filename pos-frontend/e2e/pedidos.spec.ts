import { expect, test } from '@playwright/test';
import {
  API_BASE,
  apiLogin,
  expectNotStuckLoading,
  gotoCompanyPath,
  loginAsAdmin,
  sessionToken,
} from './helpers';

test.describe('Bandeja de pedidos', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('no se queda en Cargando y muestra vacío o tabla', async ({ page }) => {
    await gotoCompanyPath(page, 'orders');
    await expect(page.getByRole('heading', { name: /bandeja de pedidos/i })).toBeVisible();
    await expectNotStuckLoading(page, /cargando pedidos/i);
    await expect(page.getByText(/no hay pedidos para mostrar/i)).toBeVisible();
  });

  test('API de pedidos responde 200 con array', async ({ page, request }) => {
    const token = await sessionToken(page);
    const res = await request.get(`${API_BASE}/api/pedidos/empresas/1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  test('sin token la API de pedidos rechaza', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/pedidos/empresas/1`);
    expect([401, 403]).toContain(res.status());
  });

  test('token inválido no lista pedidos', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/pedidos/empresas/1`, {
      headers: { Authorization: 'Bearer token-invalido' },
    });
    expect([401, 403]).toContain(res.status());
  });
});

test.describe('Pedidos API con login directo', () => {
  test('admin puede listar; respuesta es JSON array', async ({ request }) => {
    const token = await apiLogin(request);
    const res = await request.get(`${API_BASE}/api/pedidos/empresas/1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    expect(Array.isArray(await res.json())).toBeTruthy();
  });
});
