import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { ApiService } from './api.service';

export interface SinProductoServicio {
  codigo_producto_sin: number;
  codigo_actividad: string;
  descripcion_producto_sin: string;
}

export interface SinUnidadMedida {
  codigo_unidad_sin: number;
  descripcion: string;
}

export interface SinMotivoAnulacion {
  codigo_motivo_sin: number;
  descripcion: string;
}

/** Códigos de homologación SIAT de un producto. */
export interface SiatCodes {
  codigo_producto_sin?: number | null;
  codigo_unidad_medida_sin?: number | null;
}

export interface FacturaDetalleItem {
  cantidad: number;
  descripcion: string;
  precio_unitario: number;
  descuento: number;
  subtotal: number;
  codigo_producto_sin: number | null;
  codigo_unidad_medida_sin: number | null;
}

export interface FacturaSiat {
  id_factura: number;
  id_venta: number;
  numero_factura: number;
  fecha_emision: string;
  nit_emisor: string;
  nit_cliente: string;
  nombre_cliente: string;
  monto_total: number;
  cuf: string;
  estado_siat: 'EMITIDA' | 'ANULADA' | 'CONTINGENCIA';
  cuis?: string;
  cufd?: string;
  qr_code_data?: string;
  codigo_tipo_documento?: number;
  codigo_metodo_pago?: number;
  codigo_motivo_anulacion?: number | null;
  razon_social_emisor?: string;
  nombre_empresa?: string;
  sucursal?: string;
  direccion_sucursal?: string;
  ciudad_sucursal?: string;
  telefono_sucursal?: string;
  detalles?: FacturaDetalleItem[];
}

/** Tipos de documento de identidad del SIN que acepta el POS. */
export const TIPOS_DOCUMENTO_SIN = [
  { codigo: 1, nombre: 'CI - Cédula de identidad' },
  { codigo: 2, nombre: 'CEX - Cédula de identidad de extranjero' },
  { codigo: 3, nombre: 'PAS - Pasaporte' },
  { codigo: 4, nombre: 'OD - Otro documento de identidad' },
  { codigo: 5, nombre: 'NIT - Número de identificación tributaria' },
] as const;

/** Simulación de homologación SIAT: catálogos paramétricos y operaciones de factura. */
@Injectable({ providedIn: 'root' })
export class SiatService {
  private productos$?: Observable<SinProductoServicio[]>;
  private unidades$?: Observable<SinUnidadMedida[]>;
  private motivos$?: Observable<SinMotivoAnulacion[]>;

  constructor(private readonly api: ApiService) {}

  productosServicios(): Observable<SinProductoServicio[]> {
    return (this.productos$ ??= this.api
      .get<SinProductoServicio[]>('/api/sin/productos-servicios')
      .pipe(shareReplay(1)));
  }

  unidadesMedida(): Observable<SinUnidadMedida[]> {
    return (this.unidades$ ??= this.api
      .get<SinUnidadMedida[]>('/api/sin/unidades-medida')
      .pipe(shareReplay(1)));
  }

  motivosAnulacion(): Observable<SinMotivoAnulacion[]> {
    return (this.motivos$ ??= this.api
      .get<SinMotivoAnulacion[]>('/api/sin/motivos-anulacion')
      .pipe(shareReplay(1)));
  }

  factura(idFactura: number): Observable<FacturaSiat> {
    return this.api.get<FacturaSiat>(`/api/facturas/${idFactura}`);
  }

  anular(idFactura: number, codigoMotivo: number): Observable<FacturaSiat & { mensaje: string }> {
    return this.api.post<FacturaSiat & { mensaje: string }, { codigo_motivo_anulacion: number }>(
      `/api/facturas/${idFactura}/anular`,
      { codigo_motivo_anulacion: codigoMotivo },
    );
  }
}
