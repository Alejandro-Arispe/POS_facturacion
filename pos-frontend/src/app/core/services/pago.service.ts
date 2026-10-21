import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface Plan {
  id_plan: number;
  nombre: string;
  descripcion: string;
  precio: string;
}

export interface CheckoutRequest {
  id_empresa: number;
  id_plan: number;
}

export interface CheckoutResponse {
  checkout_url: string;
  session_id: string;
}

export interface ConfirmarRequest {
  session_id: string;
}

export interface SuscripcionActiva {
  estado: string;
  fecha_fin: string;
  plan_nombre: string;
}

export interface ConfirmarResponse {
  mensaje: string;
  suscripcion?: SuscripcionActiva;
}

@Injectable({
  providedIn: 'root'
})
export class PagoService {
  private readonly stripeSessionStorageKey = 'stripe_checkout_session_id';

  constructor(private apiService: ApiService) {}

  getPlanes(): Observable<Plan[]> {
    return this.apiService.get<Plan[]>('/api/planes');
  }

  crearCheckout(req: CheckoutRequest): Observable<CheckoutResponse> {
    return this.apiService.post<CheckoutResponse, CheckoutRequest>('/api/pagos/checkout', req);
  }

  confirmarPago(req: ConfirmarRequest): Observable<ConfirmarResponse> {
    return this.apiService.post<ConfirmarResponse, ConfirmarRequest>('/api/pagos/confirmar', req);
  }

  guardarSesionStripe(sessionId: string): void {
    if (typeof sessionStorage === 'undefined') {
      return;
    }

    sessionStorage.setItem(this.stripeSessionStorageKey, sessionId);
  }

  obtenerSesionStripe(): string | null {
    if (typeof sessionStorage === 'undefined') {
      return null;
    }

    return sessionStorage.getItem(this.stripeSessionStorageKey);
  }

  limpiarSesionStripe(): void {
    if (typeof sessionStorage === 'undefined') {
      return;
    }

    sessionStorage.removeItem(this.stripeSessionStorageKey);
  }
}
