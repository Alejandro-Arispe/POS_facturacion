import { expect, test } from '@playwright/test';
import { API_BASE, DEMO_ADMIN, apiLogin, loginWith } from './helpers';

test.describe('Seguridad y casos de fallo', () => {
  test('ruta protegida sin sesión redirige a login', async ({ page }) => {
    await page.goto('/company/1/dashboard');
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 });
  });

  test('login con contraseña incorrecta permanece en login', async ({ page }) => {
    await loginWith(page, DEMO_ADMIN.email, 'ClaveIncorrecta1');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText(/correo o contrasena|incorrectos|error/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  test('login con correo inexistente falla', async ({ page }) => {
    await loginWith(page, 'no.existe.e2e@example.com', 'Tienda2026!');
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByText(/correo o contrasena|incorrectos|error|no encontrado/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });

  test('superadmin sin privilegios no entra al panel', async ({ page }) => {
    await loginWith(page, DEMO_ADMIN.email, DEMO_ADMIN.password);
    await expect(page).toHaveURL(/\/company\/\d+\//, { timeout: 30_000 });
    await page.goto('/superadmin');
    await expect(page).not.toHaveURL(/\/superadmin$/, { timeout: 10_000 });
  });

  test('API empresas sin token → 401', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/empresas/mis-empresas`);
    expect([401, 403]).toContain(res.status());
  });

  test('API facturas PDF sin token → 401/403/404', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/facturas/41/pdf`);
    expect([401, 403, 404]).toContain(res.status());
  });

  test('API facturas PDF con token válido → PDF', async ({ request }) => {
    const token = await apiLogin(request);
    const list = await request.get(`${API_BASE}/api/empresas/1/facturas`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(list.ok()).toBeTruthy();
    const invoices = (await list.json()) as Array<{ id_factura: number }>;
    expect(invoices.length).toBeGreaterThan(0);
    const pdf = await request.get(`${API_BASE}/api/facturas/${invoices[0].id_factura}/pdf`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(pdf.ok()).toBeTruthy();
    expect(pdf.headers()['content-type'] ?? '').toMatch(/pdf/i);
    expect((await pdf.body()).slice(0, 4).toString('utf8')).toBe('%PDF');
  });

  test('registro exige plan (redirige a planes)', async ({ page }) => {
    await page.goto('/register');
    await expect(page).toHaveURL(/\/planes/, { timeout: 15_000 });
  });
});
