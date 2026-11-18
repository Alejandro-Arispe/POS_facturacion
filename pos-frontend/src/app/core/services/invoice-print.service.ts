import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { FacturaSiat, SiatService, TIPOS_DOCUMENTO_SIN } from './siat.service';

const METODOS_PAGO_SIN: Record<number, string> = { 1: 'EFECTIVO', 2: 'TARJETA', 7: 'TRANSFERENCIA / QR' };

/** Representación gráfica (simulada) de la factura SIAT: ticket imprimible con CUF, CUFD y QR. */
@Injectable({ providedIn: 'root' })
export class InvoicePrintService {
  private readonly siat = inject(SiatService);

  /** Abre la ventana antes de pedir los datos para que el navegador no la bloquee como popup. */
  async print(idFactura: number): Promise<void> {
    const win = window.open('', '_blank', 'width=420,height=760');
    if (!win) throw new Error('El navegador bloqueó la ventana de impresión.');
    win.document.write('<p style="font-family:sans-serif">Generando factura...</p>');
    try {
      const factura = await firstValueFrom(this.siat.factura(idFactura));
      // qrcode se carga solo al imprimir para no engordar el paquete inicial.
      const QRCode = (await import('qrcode')).default;
      const qr = factura.qr_code_data
        ? await QRCode.toDataURL(factura.qr_code_data, { margin: 1, width: 180 })
        : '';
      win.document.open();
      win.document.write(this.html(factura, qr));
      win.document.close();
      win.onload = () => win.print();
    } catch (error) {
      win.close();
      throw error;
    }
  }

  private html(f: FacturaSiat, qr: string): string {
    const e = escape;
    const money = (v: number | null | undefined) =>
      new Intl.NumberFormat('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(v) || 0);
    const fecha = new Date(f.fecha_emision);
    const tipoDoc = TIPOS_DOCUMENTO_SIN.find((t) => t.codigo === f.codigo_tipo_documento)?.nombre ?? 'CI';
    const items = (f.detalles ?? [])
      .map(
        (d) => `
        <tr><td colspan="4"><strong>${e(d.descripcion)}</strong><br>
          <small>Cód. SIN ${e(d.codigo_producto_sin ?? '-')} · Unidad SIN ${e(d.codigo_unidad_medida_sin ?? '-')}</small></td></tr>
        <tr><td>${d.cantidad}</td><td class="r">${money(d.precio_unitario)}</td>
          <td class="r">${money(d.descuento)}</td><td class="r">${money(d.subtotal)}</td></tr>`,
      )
      .join('');
    const anulada =
      f.estado_siat === 'ANULADA' ? '<div class="anulada">FACTURA ANULADA</div>' : '';
    return `<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>Factura ${f.numero_factura}</title>
<style>
  body { font-family: 'Courier New', monospace; font-size: 12px; width: 300px; margin: 12px auto; color: #000; }
  h1, h2 { text-align: center; margin: 4px 0; } h1 { font-size: 14px; } h2 { font-size: 12px; font-weight: normal; }
  .c { text-align: center; } .r { text-align: right; } hr { border: 0; border-top: 1px dashed #000; margin: 8px 0; }
  table { width: 100%; border-collapse: collapse; } td, th { padding: 2px 0; vertical-align: top; }
  .code { word-break: break-all; font-size: 10px; } .qr { display: block; margin: 8px auto; }
  .legend { font-size: 10px; text-align: center; margin-top: 6px; }
  .anulada { border: 2px solid #b00; color: #b00; text-align: center; font-weight: bold; padding: 4px; margin: 6px 0; }
  @media print { body { margin: 0 auto; } }
</style></head><body>
  <h1>FACTURA CON DERECHO A CRÉDITO FISCAL</h1>
  <h2>(Simulada - Facturación en línea SIAT)</h2>
  ${anulada}
  <div class="c">
    <strong>${e(f.razon_social_emisor ?? f.nombre_empresa ?? '')}</strong><br>
    ${e(f.sucursal ?? '')}<br>${e(f.direccion_sucursal ?? '')}${f.ciudad_sucursal ? ' - ' + e(f.ciudad_sucursal) : ''}<br>
    ${f.telefono_sucursal ? 'Tel. ' + e(f.telefono_sucursal) : ''}
  </div>
  <hr>
  <table>
    <tr><td>NIT:</td><td class="r">${e(f.nit_emisor)}</td></tr>
    <tr><td>FACTURA N°:</td><td class="r">${f.numero_factura}</td></tr>
  </table>
  <div>CUF:</div><div class="code">${e(f.cuf)}</div>
  <div>CUFD:</div><div class="code">${e(f.cufd ?? '')}</div>
  <hr>
  <table>
    <tr><td>Fecha:</td><td class="r">${fecha.toLocaleString('es-BO')}</td></tr>
    <tr><td>Nombre/Razón social:</td><td class="r">${e(f.nombre_cliente)}</td></tr>
    <tr><td>${e(tipoDoc.split(' - ')[0])}:</td><td class="r">${e(f.nit_cliente)}</td></tr>
    <tr><td>Método de pago:</td><td class="r">${METODOS_PAGO_SIN[f.codigo_metodo_pago ?? 1] ?? 'OTRO'}</td></tr>
  </table>
  <hr>
  <table>
    <tr><th>Cant.</th><th class="r">P. Unit</th><th class="r">Desc.</th><th class="r">Subtotal</th></tr>
    ${items}
  </table>
  <hr>
  <table>
    <tr><td><strong>TOTAL Bs</strong></td><td class="r"><strong>${money(f.monto_total)}</strong></td></tr>
    <tr><td>IMPORTE BASE CRÉDITO FISCAL</td><td class="r">${money(f.monto_total)}</td></tr>
  </table>
  <hr>
  ${qr ? `<img class="qr" src="${qr}" alt="Código QR de verificación" width="160" height="160">` : ''}
  <p class="legend">ESTA FACTURA CONTRIBUYE AL DESARROLLO DEL PAÍS, EL USO ILÍCITO SERÁ SANCIONADO PENALMENTE DE ACUERDO A LEY</p>
  <p class="legend">Ley N° 453: Tienes derecho a recibir información sobre las características y contenidos de los servicios que utilices.</p>
  <p class="legend">"Este documento es la Representación Gráfica de un Documento Fiscal Digital emitido en una modalidad de facturación en línea"</p>
</body></html>`;
  }
}

function escape(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string,
  );
}
