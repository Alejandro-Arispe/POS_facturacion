import { Injectable, inject } from '@angular/core';
import { ApiService } from './api.service';
import { Observable } from 'rxjs';

export interface OrderDetail {
  id_detalle: number;
  id_pedido: number;
  id_producto: number;
  cantidad: number;
  precio_unitario_estimado: string;
  descuento_estimado: string;
  subtotal_estimado: string;
}

export interface Order {
  id_pedido: number;
  id_empresa: number;
  id_sucursal: number;
  id_cliente: number;
  id_venta: number | null;
  estado: string; // 'enviado', 'en_revision', 'confirmado', 'en_preparacion', 'listo_para_recoger', 'convertido_en_venta', 'cancelado', 'rechazado'
  subtotal_estimado: string;
  descuento_estimado: string;
  total_estimado: string;
  observacion_cliente?: string;
  observacion_empresa?: string;
  fecha_creacion: string;
  fecha_confirmacion?: string;
  fecha_preparacion?: string;
  fecha_listo?: string;
  fecha_cancelacion?: string;
  detalles: OrderDetail[];
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private api = inject(ApiService);

  getOrdersByCompany(companyId: number): Observable<Order[]> {
    return this.api.get<Order[]>(`/api/pedidos/empresas/${companyId}`);
  }

  getCompanyOrders(companyId: number, branchId?: number, status?: string): Observable<Order[]> {
    let url = `/api/pedidos/empresas/${companyId}`;
    const params: string[] = [];
    if (branchId) params.push(`sucursal_id=${branchId}`);
    if (status && status !== 'todos') params.push(`estado=${status}`);
    
    if (params.length > 0) {
      url += `?${params.join('&')}`;
    }
    return this.api.get<Order[]>(url);
  }

  getOrderDetail(companyId: number, orderId: number): Observable<Order> {
    return this.api.get<Order>(`/api/pedidos/empresas/${companyId}/${orderId}`);
  }

  updateOrderStatus(companyId: number, orderId: number, status: string, reason?: string): Observable<Order> {
    return this.api.patch<Order>(`/api/pedidos/empresas/${companyId}/${orderId}/estado`, {
      estado: status,
      observacion_empresa: reason
    });
  }

  getOrderForPOS(companyId: number, branchId: number, orderId: number): Observable<Order> {
    return this.api.get<Order>(`/api/pedidos/empresas/${companyId}/${orderId}`);
  }
}

