import { Injectable } from '@angular/core';
import axios from 'axios';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { CompanyService } from '../services/company.service';
import { ApiService } from '../services/api.service';
import { environments } from '../../environments/environments';

@Injectable({ providedIn: 'root' })
export class FcmService {
  private vapidKey = environments.firebaseVapidKey;
  private firebaseConfig = environments.firebase;

  constructor(
    private readonly authService: AuthService,
    private readonly companyService: CompanyService,
    private readonly apiService: ApiService,
  ) {}

  async registerToken(apiBase?: string, idEmpresa?: number | null) {
    const base = apiBase ?? this.apiService.getBaseUrl();

    // request permission
    if (!('Notification' in window)) return null;
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return null;

    // load firebase scripts dynamically
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    const { initializeApp, getApps, getApp } = await import('firebase/app');
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    const { getMessaging, getToken, onMessage, isSupported } = await import('firebase/messaging');

    // Salir silenciosamente en browsers que no soportan FCM (Safari antiguo, Firefox sin Push API, etc.)
    const supported = await isSupported().catch(() => false);
    if (!supported) {
      console.warn('[FCM WEB] Firebase Messaging no soportado en este navegador.');
      return null;
    }

    // Bug fix #1: guard against duplicate-app error when registerToken() is
    // called multiple times in the same session (e.g. navbar re-mount).
    const app = getApps().length ? getApp() : initializeApp(this.firebaseConfig);
    console.log('[FCM WEB] Firebase inicializado');

    const messaging = getMessaging(app);
    const serviceWorkerRegistration = 'serviceWorker' in navigator
      ? await navigator.serviceWorker.register('/firebase-messaging-sw.js')
      : null;

    onMessage(messaging, (payload: any) => {
      window.dispatchEvent(
        new CustomEvent('stock-notification', {
          detail: payload,
        }),
      );
    });

    try {
      const currentToken = await getToken(messaging, {
        vapidKey: this.vapidKey,
        serviceWorkerRegistration: serviceWorkerRegistration || undefined,
      });

      if (!currentToken) {
        console.warn('[FCM WEB] getToken() no devolvió token — permisos denegados o VAPID inválido.');
        return null;
      }

      console.log('[FCM WEB] Token obtenido');

      // Bug fix #2: obtain uid_usuario from the JWT before registering.
      // The JWT claim "sub" contains the user_id as a string.
      const uid_usuario = this.obtenerUidUsuario();
      if (!uid_usuario) {
        console.warn('[FCM WEB] uid_usuario no disponible — token no se registrará hasta que el usuario inicie sesión.');
        return currentToken;
      }

      // Obtain the primary role from the JWT claim "rol" or first of "roles".
      const rol = this.obtenerRolUsuario();

      const idsEmpresa = await this.obtenerEmpresasContexto(idEmpresa);
      if (idsEmpresa.length === 0) {
        // No company context yet — return the raw token anyway.
        return currentToken;
      }

      await Promise.all(
        idsEmpresa.map((empresaId) => {
          console.log(`[FCM WEB] Registrando token uid_usuario=${uid_usuario} id_empresa=${empresaId}`);
          return axios
            .post(`${base}/notifications/register-token`, {
              token: currentToken,
              uid_usuario: uid_usuario,
              rol: rol,
              plataforma: 'web',
              id_empresa: empresaId,
            })
            .then(() => {
              console.log(`[FCM WEB] Token registrado OK para id_empresa=${empresaId}`);
            })
            .catch((err: unknown) => {
              console.error(`[FCM WEB] Error registrando token para id_empresa=${empresaId}:`, err);
            });
        }),
      );

      return currentToken;
    } catch (err) {
      console.error('[FCM WEB] Error en getToken:', err);
    }
    return null;
  }

  getApiBase(): string {
    return this.apiService.getBaseUrl();
  }

  async obtenerEmpresasContexto(idEmpresa?: number | null): Promise<number[]> {
    if (typeof idEmpresa === 'number' && Number.isFinite(idEmpresa)) {
      return [idEmpresa];
    }

    if (!this.authService.isAuthenticated()) {
      return [];
    }

    const idsEmpresa = new Set<number>();
    const companies = await firstValueFrom(this.companyService.getMisEmpresas());

    for (const company of companies) {
      const companyId = this.extraerIdEmpresa(company);
      if (companyId !== null) {
        idsEmpresa.add(companyId);
      }
    }

    return Array.from(idsEmpresa);
  }

  /**
   * Decodifica el JWT almacenado en sessionStorage y retorna el campo "sub"
   * (user_id como string) o null si no está disponible / el token es inválido.
   */
  private obtenerUidUsuario(): string | null {
    const rawToken = this.authService.getAccessToken();
    if (!rawToken) return null;

    const parts = rawToken.split('.');
    if (parts.length !== 3) return null;

    try {
      const padded = parts[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(Math.ceil(parts[1].length / 4) * 4, '=');
      const payload = JSON.parse(atob(padded)) as { sub?: string; id?: string | number };
      const uid = payload.sub ?? (payload.id != null ? String(payload.id) : null);
      return uid && uid.trim() !== '' ? uid : null;
    } catch {
      return null;
    }
  }

  /**
   * Extrae el rol principal del JWT. Usa el claim "rol" (string) o el primer
   * elemento del claim "roles" (array). Retorna null si no hay rol.
   */
  private obtenerRolUsuario(): string | null {
    const roles = this.authService.getUserRoles();
    if (roles.length > 0) return roles[0];
    return null;
  }

  private extraerIdEmpresa(company: {
    idEmpresa?: string | number;
    id_empresa?: string | number;
    empresa_id?: string | number;
    id?: string | number;
  }): number | null {
    const rawId = company.idEmpresa ?? company.id_empresa ?? company.empresa_id ?? company.id;
    const parsedId = typeof rawId === 'number' ? rawId : Number(rawId);
    return Number.isFinite(parsedId) ? parsedId : null;
  }

}
