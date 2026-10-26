import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface Plan {
  id_plan: number;
  nombre: string;
  descripcion: string;
  precio: string;
  moneda: string;
  limites: {
    codigo: string;
    max_usuarios: number;
    max_sucursales: number;
    max_cajas: number;
    max_productos: number;
    reportes: string;
    pedidos: boolean;
    predicciones: boolean;
  };
}

export interface CheckoutRequest {
  id_empresa?: number;
  id_plan: number;
  frontend_origin?: string;
}

export interface CheckoutResponse {
  checkout_url: string;
  session_id: string;
  moneda: string;
}

export interface ConfirmarRequest {
  session_id: string;
}

export interface SuscripcionActiva {
  estado: string;
  id_empresa: number;
  id_plan: number;
  fecha_fin: string | null;
  plan_nombre: string;
}

export interface ConfirmarResponse {
  mensaje: string;
  suscripcion?: SuscripcionActiva;
  compra?: CompraPlanDisponible;
}

export interface CompraPlanDisponible {
  id_compra_plan: number;
  id_plan: number;
  plan_nombre: string;
  estado: string;
  gratuito: boolean;
  id_empresa: number | null;
}

@Injectable({
  providedIn: 'root',
})
export class PagoService {
  private readonly stripeSessionStorageKey = 'stripe_checkout_session_id';

  constructor(private apiService: ApiService) {}

  getPlanes(): Observable<Plan[]> {
    return this.apiService.get<Plan[]>('/api/planes').pipe(
      map((planes) => {
        if (planes.some((plan) => !plan.limites || plan.moneda !== 'BOB')) {
          throw {
            error: {
              detail:
                'Reinicia el backend para cargar el catálogo actualizado de planes en bolivianos.',
            },
          };
        }
        return planes;
      }),
    );
  }

  crearCheckout(req: CheckoutRequest): Observable<CheckoutResponse> {
    return this.apiService.post<CheckoutResponse, CheckoutRequest>('/api/pagos/checkout', {
      ...req,
      frontend_origin: window.location.origin,
    });
  }

  getPlanesDisponibles(): Observable<CompraPlanDisponible[]> {
    return this.apiService.get<CompraPlanDisponible[]>('/api/pagos/planes-disponibles');
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
