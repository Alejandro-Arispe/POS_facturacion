import { beforeEach, describe, expect, it } from 'vitest';
import { OnboardingService } from './onboarding.service';

describe('Estado del asistente', () => {
  let owner = '1';
  const auth = {
    getAccessToken: () => 'header.' + btoa(JSON.stringify({ sub: owner })) + '.signature',
  };
  beforeEach(() => {
    sessionStorage.clear();
    owner = '1';
  });

  it('conserva solo planes con identificadores válidos', () => {
    const flow = new OnboardingService(auth as never);
    expect(flow.selectPlan('abc')).toBeNull();
    expect(flow.selectPlan('-1')).toBeNull();
    expect(flow.selectPlan('2')).toBe(2);
    expect(new OnboardingService(auth as never).plan).toBe(2);
  });

  it('recupera la empresa sin duplicarla y no la muestra a otra cuenta', () => {
    const flow = new OnboardingService(auth as never);
    flow.saveCompany(8, 'Mi tienda', 2);
    expect(flow.company?.id).toBe(8);
    owner = '2';
    expect(flow.company).toBeNull();
  });

  it('al completar elimina la empresa pendiente, el plan y el correo', () => {
    const flow = new OnboardingService(auth as never);
    flow.selectPlan(2);
    flow.pendingEmail = 'demo@example.invalid';
    flow.saveCompany(8, 'Mi tienda', 2);
    flow.complete();
    expect(flow.company).toBeNull();
    expect(flow.plan).toBeNull();
    expect(flow.pendingEmail).toBe('');
  });
});
