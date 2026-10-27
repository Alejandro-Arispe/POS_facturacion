import { Injectable } from '@angular/core';
import { signal } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from './api.service';
import { CompanyPermissionsService } from './company-permissions.service';

export interface RegisterRequest {
  email: string;
  contrasena: string;
  nombre_completo: string;
  fecha_nacimiento: string;
  genero: string;
  telefono: string;
  documento: string;
}

export interface RegisterResponse {
  usuario_id?: number;
  email?: string;
  activo?: boolean;
  mensaje?: string;
  message?: string;
  email_enviado?: boolean | string | number;
  emailEnviado?: boolean | string | number;
  registered?: boolean | string | number;
  data?: {
    email_enviado?: boolean | string | number;
    emailEnviado?: boolean | string | number;
    registered?: boolean | string | number;
  };
}

export interface VerifyCodeRequest {
  email: string;
  codigo: string;
}

export interface VerifyCodeResponse {
  mensaje?: string;
  activo?: boolean;
  [key: string]: unknown;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ForgotPasswordResponse {
  mensaje?: string;
  message?: string;
  [key: string]: unknown;
}

export interface LoginRequest {
  email: string;
  contrasena: string;
}

export interface LoginResponse {
  access_token: string;
  token_type: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly tokenKey = 'access_token';
  private readonly cashRegisterOpenPolicyPrefix = 'cash-register-open-policy:';
  readonly isAuthenticated = signal(this.hasValidStoredToken());

  constructor(
    private readonly apiService: ApiService,
    private readonly permissions: CompanyPermissionsService,
  ) {}

  register(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.apiService.post<RegisterResponse, RegisterRequest>('/api/auth/register', payload);
  }

  login(payload: LoginRequest): Observable<LoginResponse> {
    return this.apiService.post<LoginResponse, LoginRequest>('/api/auth/login', payload);
  }

  verifyCode(payload: VerifyCodeRequest): Observable<VerifyCodeResponse> {
    return this.apiService.post<VerifyCodeResponse, VerifyCodeRequest>(
      '/api/auth/verify-code',
      payload,
    );
  }

  forgotPassword(payload: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    return this.apiService.post<ForgotPasswordResponse, ForgotPasswordRequest>(
      '/api/auth/forgot-password',
      payload,
    );
  }

  resendCode(payload: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    return this.apiService.post<ForgotPasswordResponse, ForgotPasswordRequest>(
      '/api/auth/resend-code',
      payload,
    );
  }

  saveSession(accessToken: string): void {
    if (this.isTokenExpired(accessToken)) {
      this.logout();
      return;
    }

    sessionStorage.setItem(this.tokenKey, accessToken);
    this.isAuthenticated.set(true);
  }

  getAccessToken(): string | null {
    const token = sessionStorage.getItem(this.tokenKey);

    if (!token) {
      this.isAuthenticated.set(false);
      return null;
    }

    if (this.isTokenExpired(token)) {
      this.logout();
      return null;
    }

    this.isAuthenticated.set(true);
    return token;
  }

  getUserRoles(): string[] {
    const token = sessionStorage.getItem(this.tokenKey);
    if (!token) return [];
    const payload = this.decodeTokenPayload(token) as any;
    // possible claim names: role, rol, roles
    if (payload?.roles && Array.isArray(payload.roles))
      return payload.roles.map((r: any) =>
        typeof r === 'string' ? r.toLowerCase() : String(r).toLowerCase(),
      );
    if (payload?.role && typeof payload.role === 'string') return [payload.role.toLowerCase()];
    if (payload?.rol && typeof payload.rol === 'string') return [payload.rol.toLowerCase()];
    return [];
  }

  isSuperAdmin(): boolean {
    return this.getUserRoles().includes('superadmin');
  }

  isAdmin(): boolean {
    const roles = this.getUserRoles();
    return roles.includes('administrador') || roles.includes('admin');
  }

  logout(): void {
    this.permissions.clearPermissions();
    sessionStorage.removeItem(this.tokenKey);
    this.clearCashRegisterOpenPolicies();
    this.isAuthenticated.set(false);
  }

  private clearCashRegisterOpenPolicies(): void {
    for (let index = sessionStorage.length - 1; index >= 0; index -= 1) {
      const key = sessionStorage.key(index);

      if (key?.startsWith(this.cashRegisterOpenPolicyPrefix)) {
        sessionStorage.removeItem(key);
      }
    }
  }

  private isTokenExpired(token: string): boolean {
    const payload = this.decodeTokenPayload(token);
    const exp = payload?.exp;

    if (typeof exp !== 'number') {
      return true;
    }

    const nowInSeconds = Math.floor(Date.now() / 1000);
    return exp <= nowInSeconds;
  }

  private decodeTokenPayload(token: string): { exp?: number } | null {
    const parts = token.split('.');

    if (parts.length !== 3) {
      return null;
    }

    try {
      const payload = parts[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(Math.ceil(parts[1].length / 4) * 4, '=');

      const decodedPayload = atob(payload);
      return JSON.parse(decodedPayload) as { exp?: number };
    } catch {
      return null;
    }
  }

  private hasValidStoredToken(): boolean {
    const token = sessionStorage.getItem(this.tokenKey);

    if (!token) {
      return false;
    }

    if (this.isTokenExpired(token)) {
      sessionStorage.removeItem(this.tokenKey);
      return false;
    }

    return true;
  }
}
