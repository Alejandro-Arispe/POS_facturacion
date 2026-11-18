import { expect, type APIRequestContext, type Page } from '@playwright/test';

export const DEMO_ADMIN = {
  email: process.env['E2E_ADMIN_EMAIL'] ?? 'e2e.demo@example.com',
  password: process.env['E2E_ADMIN_PASSWORD'] ?? 'Tienda2026!',
};

export const DEMO_CASHIER = {
  email: process.env['E2E_CASHIER_EMAIL'] ?? 'cajero.centro@example.com',
  password: process.env['E2E_CASHIER_PASSWORD'] ?? 'Tienda2026!',
};

export const API_BASE = process.env['E2E_API_BASE'] ?? 'http://localhost:8000';

export async function loginAsAdmin(page: Page): Promise<void> {
  await loginWith(page, DEMO_ADMIN.email, DEMO_ADMIN.password);
  await expect(page).toHaveURL(/\/company\/\d+\/(dashboard|branches)|\/my-companies/, {
    timeout: 30_000,
  });
}

export async function loginWith(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel(/correo electronico/i).fill(email);
  await page.getByLabel(/contrasena/i).fill(password);
  await page.getByRole('button', { name: /iniciar sesion/i }).click();
}

export async function gotoCompanyPath(page: Page, relative: string): Promise<string> {
  const match = page.url().match(/\/company\/(\d+)/);
  const companyId = match?.[1] ?? '1';
  await page.goto(`/company/${companyId}/${relative}`);
  return companyId;
}

export async function sessionToken(page: Page): Promise<string> {
  const token = await page.evaluate(
    () => sessionStorage.getItem('access_token') ?? localStorage.getItem('access_token'),
  );
  expect(token, 'token de sesión').toBeTruthy();
  return token!;
}

export async function apiLogin(
  request: APIRequestContext,
  email = DEMO_ADMIN.email,
  password = DEMO_ADMIN.password,
): Promise<string> {
  const res = await request.post(`${API_BASE}/api/auth/login`, {
    data: { email, contrasena: password },
  });
  expect(res.ok(), `login API ${email}`).toBeTruthy();
  const body = (await res.json()) as { access_token?: string; accessToken?: string };
  const token = body.access_token ?? body.accessToken;
  expect(token).toBeTruthy();
  return token!;
}

export async function expectNotStuckLoading(page: Page, loadingText: RegExp): Promise<void> {
  await expect(page.getByText(loadingText)).toHaveCount(0, { timeout: 20_000 });
}
