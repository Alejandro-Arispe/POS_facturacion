import { expect, test } from '@playwright/test';
import { API_BASE, apiLogin, loginAsAdmin, sessionToken } from './helpers';

test.describe('Notificaciones autenticadas', () => {
  test('historial con token de sesión responde 200', async ({ page, request }) => {
    await loginAsAdmin(page);
    const token = await sessionToken(page);

    const res = await request.get(`${API_BASE}/notifications/history/empresas/1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status(), await res.text()).toBe(200);
    const body = (await res.json()) as { items?: unknown };
    expect(Array.isArray(body.items)).toBeTruthy();
  });

  test('historial sin token responde 401', async ({ request }) => {
    const res = await request.get(`${API_BASE}/notifications/history/empresas/1`);
    expect(res.status()).toBe(401);
  });

  test('navbar puede abrir centro de notificaciones sin error de red 401', async ({ page }) => {
    await loginAsAdmin(page);

    const failed401: string[] = [];
    page.on('response', (response) => {
      if (response.url().includes('/notifications/') && response.status() === 401) {
        failed401.push(response.url());
      }
    });

    await page.getByRole('button', { name: /notificaciones/i }).click();
    await expect(page.getByRole('heading', { name: /notificaciones/i }).first()).toBeVisible({
      timeout: 10_000,
    });
    await page.waitForTimeout(1500);
    expect(failed401, `401 inesperados: ${failed401.join(', ')}`).toEqual([]);
  });

  test('API login + historial consistente', async ({ request }) => {
    const token = await apiLogin(request);
    const res = await request.get(`${API_BASE}/notifications/history/empresas/1`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
  });
});
