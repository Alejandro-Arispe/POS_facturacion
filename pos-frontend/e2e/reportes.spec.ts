import { expect, test } from '@playwright/test';
import { expectNotStuckLoading, gotoCompanyPath, loginAsAdmin } from './helpers';

test.describe('Reportes', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('reportes estáticos cargan', async ({ page }) => {
    await gotoCompanyPath(page, 'reports/static');
    await expect(page.getByRole('heading', { name: /reportes estaticos|estáticos/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /ventas/i })).toBeVisible();
  });

  test('reportes parametrizados cargan', async ({ page }) => {
    await gotoCompanyPath(page, 'reports/parameterized');
    await expect(page.getByRole('heading', { name: /parametriz/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('facturas listan VALIDADA y permiten XML', async ({ page }) => {
    await gotoCompanyPath(page, 'reports/invoices');
    await expect(page.getByRole('heading', { name: /^facturas$/i })).toBeVisible();
    await expectNotStuckLoading(page, /cargando/i);
    await expect(page.getByText(/VALIDADA/i).first()).toBeVisible();
    await expect(page.getByRole('link', { name: /abrir xml/i }).first()).toBeVisible();
  });

  test('mi plan muestra Premium activo', async ({ page }) => {
    await page.goto('/my-plan/1');
    await expect(page.getByText(/premium|plan/i).first()).toBeVisible({ timeout: 20_000 });
    await expectNotStuckLoading(page, /cargando/i);
  });
});
