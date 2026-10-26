import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  constructor(private readonly auth: AuthService) {}
  private readonly planKey = 'pos_onboarding_plan';
  private readonly emailKey = 'pos_onboarding_email';

  selectPlan(value: unknown): number | null {
    const id = Number(value);
    if (!Number.isSafeInteger(id) || id <= 0) return null;
    sessionStorage.setItem(this.planKey, String(id));
    return id;
  }

  get plan(): number | null {
    const value = Number(sessionStorage.getItem(this.planKey));
    return Number.isSafeInteger(value) && value > 0 ? value : null;
  }

  set pendingEmail(email: string) {
    sessionStorage.setItem(this.emailKey, email);
  }
  get pendingEmail(): string {
    return sessionStorage.getItem(this.emailKey) ?? '';
  }
  clearPendingEmail(): void {
    sessionStorage.removeItem(this.emailKey);
  }
  saveCompany(id: number, name: string, planId: number): void {
    sessionStorage.setItem(
      'pos_onboarding_company',
      JSON.stringify({ id, name, planId, owner: this.owner() }),
    );
  }
  get company(): { id: number; name: string; planId: number } | null {
    try {
      const value = JSON.parse(sessionStorage.getItem('pos_onboarding_company') ?? 'null');
      return value?.owner &&
        value.owner === this.owner() &&
        Number.isSafeInteger(value.id) &&
        value.id > 0
        ? value
        : null;
    } catch {
      return null;
    }
  }
  complete(): void {
    sessionStorage.removeItem(this.planKey);
    sessionStorage.removeItem('pos_onboarding_company');
    this.clearPendingEmail();
  }
  private owner(): string | null {
    try {
      return String(
        JSON.parse(
          atob(
            (this.auth.getAccessToken()?.split('.')[1] ?? '').replace(/-/g, '+').replace(/_/g, '/'),
          ),
        ).sub,
      );
    } catch {
      return null;
    }
  }
}
