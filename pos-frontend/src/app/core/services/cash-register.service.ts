import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiService } from './api.service';

export interface CreateCashRegisterRequest {
  nombre: string;
  codigo: string;
}

export interface UpdateCashRegisterRequest extends CreateCashRegisterRequest {
  activo: boolean;
}

export interface OpenCashRegisterSessionRequest {
  monto_inicial: number;
  nota: string;
}

export interface CashRegisterSessionResponse {
  id_caja_sesion: number;
  id_caja: number;
  id_usuario: number;
  fecha_apertura: string;
  fecha_cierre: string | null;
  monto_inicial: number;
  monto_final: number | null;
  estado: 'Abierto' | string;
  nota: string;
}

export interface CreateCashRegisterMovementRequest {
  concepto: string;
  monto: number;
  id_tipo_movimiento_caja: number;
  id_metodo_pago: number;
}

export interface CashRegisterMovementResponse {
  id_movimiento_caja: number;
  id_metodo_pago: number | null;
  id_tipo_movimiento_caja: number;
  id_caja_sesion: number;
  id_usuario: number;
  fecha: string;
  monto: number;
  concepto: string;
}

export interface CashRegisterMovementListItem {
  id_movimiento_caja: number;
  id_metodo_pago: number | null;
  id_tipo_movimiento_caja: number;
  id_caja_sesion: number;
  id_usuario: number;
  fecha: string;
  monto: number;
  concepto: string;
}

export interface CashRegisterClosingMovementItem {
  id_movimiento_caja: number;
  id_metodo_pago: number | null;
  id_tipo_movimiento_caja: number;
  id_caja_sesion: number;
  id_usuario: number;
  fecha: string;
  monto: number;
  concepto: string;
}

export interface CashRegisterClosingPaymentSummary {
  id_metodo_pago: number;
  metodo_pago: string;
  total_ingresos: number;
  total_egresos: number;
  monto_esperado: number;
  movimientos: CashRegisterClosingMovementItem[];
}

export interface CashRegisterClosingSummaryResponse {
  id_caja_sesion: number;
  monto_esperado_total: number;
  resumen_por_metodo_pago: CashRegisterClosingPaymentSummary[];
}

export interface CashRegisterClosingRequestItem {
  id_metodo_pago: number;
  monto_esperado: number;
  monto_real: number;
  diferencia: number;
  observacion: string | null;
}

export interface CashRegisterClosingMovementResponse {
  id_movimiento_caja: number;
  id_metodo_pago: number | null;
  id_tipo_movimiento_caja: number;
  id_caja_sesion: number;
  id_usuario: number;
  fecha: string;
  monto: number;
  concepto: string;
}

export interface CashRegisterClosingDetailResponse {
  id_caja_cierre_detalle: number;
  id_metodo_pago: number;
  id_caja_sesion: number;
  monto_esperado: number;
  monto_real: number;
  diferencia: number;
  observacion: string | null;
}

export interface CashRegisterClosingResponse {
  id_caja_sesion: number;
  monto_inicial: number;
  monto_total_real: number;
  monto_total_esperado: number;
  monto_final: number;
  estado: string;
  fecha_cierre: string;
  movimiento_cierre: CashRegisterClosingMovementResponse;
  cierres: CashRegisterClosingDetailResponse[];
}

export interface SaleDetailRequest {
  id_producto: number;
  cantidad: number;
  precio_unitario: number;
  descuento: number;
  subtotal: number;
  descripcion: string;
}

export interface SalePaymentRequest {
  id_metodo_pago: number;
  monto: number;
}

export interface CreateSaleRequest {
  id_tipo_venta: number;
  id_cliente: number | null;
  id_metodo_pago: number | null;
  id_pedido?: number | null;
  factura_linea: boolean;
  subtotal: number;
  descuento_total: number;
  total: number;
  estado: 'Pendiente' | string;
  pagos?: SalePaymentRequest[];
  detalles: SaleDetailRequest[];
}

export interface CreateSaleResponse {
  id_venta: number;
  id_usuario: number;
  id_caja_sesion: number;
  subtotal: number;
  descuento_total: number;
  total: number;
  estado: 'Pendiente' | string;
  detalles: Array<{
    id_detalle_venta: number;
    id_producto: number;
    cantidad: number;
    precio_unitario: number;
    descuento: number;
    subtotal: number;
    descripcion: string;
  }>;
}

export interface SaleHistoryDetailItem {
  id_detalle_venta: number;
  id_venta?: number;
  id_producto: number;
  cantidad: number;
  precio_unitario: number;
  descuento: number;
  subtotal: number;
  total?: number;
  descripcion: string | null;
  producto?: {
    id_producto: number;
    nombre: string;
    codigo_barra?: string;
    unidad_medida?: string;
  };
}

export interface SaleHistoryPaymentItem {
  id_venta_pago?: number;
  id_venta?: number;
  id_metodo_pago: number;
  monto: number;
  fecha: string;
  metodo_pago?: {
    id_metodo_pago: number;
    nombre: string;
    descripcion: string | null;
  } | null;
}

export interface SaleHistoryResponse {
  id_venta: number;
  id_tipo_venta?: number;
  tipo_venta_nombre?: string;
  id_cliente?: number | null;
  id_caja_sesion: number;
  id_usuario: number;
  subtotal: number;
  descuento_total: number;
  total: number;
  fecha: string;
  estado: string;
  id_metodo_pago?: number | null;
  metodo_pago?: {
    id_metodo_pago: number;
    nombre: string;
    descripcion: string | null;
  } | null;
  detalles: SaleHistoryDetailItem[];
  pagos: SaleHistoryPaymentItem[];
}

export interface CreditPaymentRequestItem {
  id_metodo_pago: number;
  monto_pagado: string;
}

export interface RegisterCreditPaymentRequest {
  id_cxc: number;
  pagos_credito: CreditPaymentRequestItem[];
}

export interface RegisteredCreditPaymentItem {
  id_pago_credito: number;
  id_metodo_pago: number;
  monto_pagado: string | number;
  fecha_pago: string;
  metodo_pago: {
    id_metodo_pago: number;
    nombre: string;
    descripcion: string | null;
  };
}

export interface CreditPaymentMovement {
  id_movimiento_caja: number;
  id_metodo_pago: number;
  id_tipo_movimiento_caja: number;
  id_caja_sesion: number;
  id_usuario: number;
  fecha: string;
  monto: string | number;
  concepto: string;
}

export interface RegisterCreditPaymentResponse {
  id_cxc: number;
  id_caja_sesion: number;
  monto_credito: string | number;
  saldo_anterior: string | number;
  total_pagado: string | number;
  saldo_pendiente: string | number;
  estado: string;
  pagos_credito: RegisteredCreditPaymentItem[];
  movimientos_caja: CreditPaymentMovement[];
}

export interface CashRegisterSessionActiveInfo {
  id_caja_sesion: number;
  id_usuario_apertura: number;
  fecha_apertura: string;
  es_usuario_actual: boolean;
}

export interface CashRegisterResponse {
  id?: number;
  id_caja: number;
  id_sucursal: number;
  codigo: string;
  nombre: string;
  fecha_creacion: string;
  fecha_registro?: string;
  activo: boolean;
  estado_operativo?: 'inactiva' | 'disponible' | 'abierta_por_mi' | 'en_uso';
  sesion_activa?: CashRegisterSessionActiveInfo | null;
}

export type CashRegisterListResponse =
  | CashRegisterResponse[]
  | {
      cajas?: CashRegisterResponse[];
      items?: CashRegisterResponse[];
      data?: CashRegisterResponse[] | {
        cajas?: CashRegisterResponse[];
        items?: CashRegisterResponse[];
      };
    };

@Injectable({
  providedIn: 'root',
})
export class CashRegisterService {
  constructor(private readonly apiService: ApiService) {}

  getCajasSucursal(idEmpresa: string, idSucursal: string): Observable<CashRegisterListResponse> {
    return this.apiService.get<CashRegisterListResponse>(
      `/api/empresas/${idEmpresa}/sucursales/${idSucursal}/cajas`,
    );
  }

  crearCaja(idSucursal: string, payload: CreateCashRegisterRequest): Observable<CashRegisterResponse> {
    return this.apiService.post<CashRegisterResponse, CreateCashRegisterRequest>(
      `/api/sucursales/${idSucursal}/cajas`,
      payload,
    );
  }

  abrirSesionCaja(
    idCaja: string,
    payload: OpenCashRegisterSessionRequest,
  ): Observable<CashRegisterSessionResponse> {
    return this.apiService.post<CashRegisterSessionResponse, OpenCashRegisterSessionRequest>(
      `/api/cajas/${idCaja}/sesiones`,
      payload,
    );
  }

  crearMovimientoCajaSesion(
    idCajaSesion: string | number,
    payload: CreateCashRegisterMovementRequest,
  ): Observable<CashRegisterMovementResponse> {
    return this.apiService.post<CashRegisterMovementResponse, CreateCashRegisterMovementRequest>(
      `/api/cajas/sesiones/${idCajaSesion}/movimientos`,
      payload,
    );
  }

  registrarVentaSesionCaja(
    idCajaSesion: string | number,
    payload: CreateSaleRequest,
  ): Observable<CreateSaleResponse> {
    return this.apiService.post<CreateSaleResponse, CreateSaleRequest>(
      `/api/ventas/sesiones/${idCajaSesion}/ventas`,
      payload,
    );
  }

  getVentasSesionCaja(idCajaSesion: string | number): Observable<SaleHistoryResponse[]> {
    return this.apiService.get<SaleHistoryResponse[]>(`/api/ventas/sesiones/${idCajaSesion}/ventas`);
  }

  registrarPagoCreditoSesionCaja(
    idCajaSesion: string | number,
    payload: RegisterCreditPaymentRequest,
  ): Observable<RegisterCreditPaymentResponse> {
    return this.apiService.post<RegisterCreditPaymentResponse, RegisterCreditPaymentRequest>(
      `/api/ventas/sesiones/${idCajaSesion}/pagos-credito`,
      payload,
    );
  }

  getMovimientosCajaSesion(idCajaSesion: string | number): Observable<CashRegisterMovementListItem[]> {
    return this.apiService.get<CashRegisterMovementListItem[]>(
      `/api/cajas/sesiones/${idCajaSesion}/movimientos`,
    );
  }

  getResumenCierreCajaSesion(idCajaSesion: string | number): Observable<CashRegisterClosingSummaryResponse> {
    return this.apiService.get<CashRegisterClosingSummaryResponse>(
      `/api/cajas/sesiones/${idCajaSesion}/movimientos/resumen`,
    );
  }

  cerrarSesionCaja(
    idCajaSesion: string | number,
    payload: CashRegisterClosingRequestItem[],
  ): Observable<CashRegisterClosingResponse> {
    return this.apiService.post<CashRegisterClosingResponse, CashRegisterClosingRequestItem[]>(
      `/api/cajas/sesiones/${idCajaSesion}/cierres`,
      payload,
    );
  }

  actualizarCaja(
    idEmpresa: string,
    idSucursal: string,
    idCaja: number,
    payload: UpdateCashRegisterRequest,
  ): Observable<CashRegisterResponse> {
    return this.apiService.put<CashRegisterResponse, UpdateCashRegisterRequest>(
      `/api/empresas/${idEmpresa}/sucursales/${idSucursal}/cajas/${idCaja}`,
      payload,
    );
  }
}
