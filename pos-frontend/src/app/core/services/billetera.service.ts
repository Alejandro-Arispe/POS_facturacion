import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from './api.service';

/** Cobro QR creado en la Billetera (pasarela de pagos). */
export interface CobroQr {
  id: string;
  estado: EstadoCobroQr;
  monto: number;
  referencia: string;
  vence: string;
  qr_imagen: string;
  qr_texto: string;
}

export type EstadoCobroQr = 'CREATED' | 'SCANNED' | 'PROCESSING' | 'PAID' | 'EXPIRED' | 'CANCELLED';

export interface EstadoCobro {
  id: string;
  estado: EstadoCobroQr;
  monto: number;
  pagado_por: string;
}

export interface BilleteraConfig {
  qr_habilitado: boolean;
  tarjeta_habilitada: boolean;
}

export interface AutorizacionTarjeta {
  status: 'APPROVED' | 'DECLINED';
  approvalCode?: string;
  message: string;
  cardLast4?: string;
}

/**
 * Pasarela QR de la Billetera. El frontend nunca habla directo con la billetera: todo pasa por el
 * backend, que guarda la API key y verifica los webhooks.
 */
@Injectable({ providedIn: 'root' })
export class BilleteraService {
  private readonly api = inject(ApiService);

  config(): Observable<BilleteraConfig> {
    return this.api.get<BilleteraConfig>('/api/billetera/config');
  }

  crearCobro(idEmpresa: number, idSucursal: number | null, monto: number, descripcion: string): Observable<CobroQr> {
    return this.api.post<CobroQr, unknown>('/api/billetera/cobros', {
      id_empresa: idEmpresa,
      id_sucursal: idSucursal,
      monto,
      descripcion,
    });
  }

  estado(id: string): Observable<EstadoCobro> {
    return this.api.get<EstadoCobro>(`/api/billetera/cobros/${id}`);
  }

  cancelar(id: string): Observable<unknown> {
    return this.api.post<unknown, unknown>(`/api/billetera/cobros/${id}/cancelar`, {});
  }

  autorizarTarjeta(datos: {
    id_empresa: number;
    id_sucursal: number | null;
    numero: string;
    mes: number;
    anio: number;
    cvv: string;
    titular: string;
    monto: number;
    comercio: string;
  }): Observable<AutorizacionTarjeta> {
    return this.api.post<AutorizacionTarjeta, unknown>('/api/billetera/tarjeta/autorizar', datos);
  }
}
