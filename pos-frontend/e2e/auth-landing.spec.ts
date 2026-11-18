import { expect, test } from '@playwright/test';
import { DEMO_ADMIN, loginAsAdmin } from './helpers';

test.describe('Landing y autenticación', () => {
  test('la landing carga con marca y accesos', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: /iniciar sesion/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /registrar/i })).toBeVisible();
  });

  test('planes muestra opciones de suscripción', async ({ page }) => {
    await page.goto('/planes');
    await expect(page.getByRole('heading', { name: /empieza pequeño/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /gratuito/i })).toBeVisible();
  });

  test('login admin demo llega al dashboard', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page).toHaveURL(/\/company\/\d+\/dashboard/);
    await expect(page.getByRole('heading', { name: /resumen del negocio/i })).toBeVisible();
    await expect(page.getByText(/minimarket don carlos/i).first()).toBeVisible();
  });

  test('credenciales inválidas muestran error', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/correo electronico/i).fill(DEMO_ADMIN.email);
    await page.getByLabel(/contrasena/i).fill('ClaveIncorrecta1');
    await page.getByRole('button', { name: /iniciar sesion/i }).click();
    await expect(page.getByText(/correo o contrasena|incorrectos|error/i).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page).toHaveURL(/\/login/);
  });
});
