import { Pipe, PipeTransform } from '@angular/core';

const ESTADOS_PEDIDO: Record<string, string> = {
  enviado: 'Enviado por cliente',
  en_revision: 'En revisión',
  confirmado: 'Confirmado',
  rechazado: 'Rechazado',
  cancelado: 'Cancelado',
  en_preparacion: 'En preparación',
  listo_para_recoger: 'Listo para recoger',
  convertido_en_venta: 'Cobrado en caja',
  entregado: 'Entregado',
};

/** Etiqueta en español de un estado de pedido; devuelve el valor original si no se conoce. */
export function estadoPedido(estado: string): string {
  return ESTADOS_PEDIDO[estado] ?? estado;
}

/** {{ pedido.estado | estadoPedido }} */
@Pipe({ name: 'estadoPedido', standalone: true })
export class EstadoPedidoPipe implements PipeTransform {
  transform(estado: string | null | undefined): string {
    return estado ? estadoPedido(estado) : '';
  }
}
