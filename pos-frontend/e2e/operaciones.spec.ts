import { expect, test } from '@playwright/test';
import { expectNotStuckLoading, gotoCompanyPath, loginAsAdmin } from './helpers';

test.describe('Flujos operativos autenticados', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('dashboard muestra indicadores del día', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /ventas de hoy/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /ticket promedio/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /cajas abiertas/i })).toBeVisible();
  });

  test('catálogo de productos lista ítems demo', async ({ page }) => {
    await gotoCompanyPath(page, 'products');
    await expect(page.getByRole('heading', { name: /lista de productos/i })).toBeVisible();
    await expectNotStuckLoading(page, /cargando/i);
    await expect(page.getByText(/12 productos/i).first()).toBeVisible();
    await expect(page.getByText(/aceite fino/i).first()).toBeVisible();
  });

  test('categorías de productos cargan', async ({ page }) => {
    await gotoCompanyPath(page, 'products/categories');
    await expect(page.getByRole('heading', { name: /categor/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('sucursales muestra Centro y Zona Sur', async ({ page }) => {
    await gotoCompanyPath(page, 'branches');
    await expect(page.getByRole('heading', { name: /^sucursales$/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /sucursal centro/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /sucursal zona sur/i })).toBeVisible();
  });

  test('clientes abre el catálogo', async ({ page }) => {
    await gotoCompanyPath(page, 'clients');
    await expect(page.getByRole('heading', { name: /catalogo de clientes/i })).toBeVisible();
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('categorías de clientes cargan', async ({ page }) => {
    await gotoCompanyPath(page, 'clients/categories');
    await expect(page.getByRole('heading', { name: /categor/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('cajas de sucursal permiten abrir caja', async ({ page }) => {
    await gotoCompanyPath(page, 'branch/1/cash-register');
    await expect(page.getByRole('heading', { name: /^cajas$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /abrir caja/i }).first()).toBeVisible();
  });

  test('inventario de sucursal carga sin quedarse cargando', async ({ page }) => {
    await gotoCompanyPath(page, 'branch/1/inventario');
    await expect(page.getByRole('heading', { name: /inventario/i })).toBeVisible();
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('personal/staff carga', async ({ page }) => {
    await gotoCompanyPath(page, 'users/staff');
    await expect(page.getByRole('heading', { name: /personal|staff|emplead/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('roles carga', async ({ page }) => {
    await gotoCompanyPath(page, 'users/rols');
    await expect(page.getByRole('heading', { name: /rol/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('bitácora carga', async ({ page }) => {
    await gotoCompanyPath(page, 'audit-log');
    await expect(page.getByRole('heading', { name: /bit[aá]cora|auditor/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });

  test('configuración de empresa carga', async ({ page }) => {
    await page.goto('/administrator/settings?companyId=1');
    await expect(page.getByRole('heading', { name: /configur/i }).first()).toBeVisible({
      timeout: 20_000,
    });
    await expectNotStuckLoading(page, /cargando/i);
  });
});
