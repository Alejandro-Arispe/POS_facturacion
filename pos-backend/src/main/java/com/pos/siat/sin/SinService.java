package com.pos.siat.sin;

import java.time.LocalDateTime;

/** Servicios del SIN (Impuestos Nacionales) que usa la facturación Computarizada en Línea. */
public interface SinService {

    record Cufd(String codigo, String codigoControl, LocalDateTime obtenido, LocalDateTime vence) {}

    /** estado: VALIDADA, OBSERVADA, RECHAZADA, ANULADA o NO_ENCONTRADA. */
    record Respuesta(String estado, String codigoRecepcion, String mensaje) {}

    /** false simula la caída del SIN: cada operación lanza SinNoDisponibleException. */
    boolean disponible();

    void setDisponible(boolean disponible);

    String solicitarCuis(String nit, Integer sucursal, Integer puntoVenta);

    Cufd solicitarCufd(String nit, Integer sucursal, Integer puntoVenta, String cuis);

    /** Recibe el XML y su hash SHA-256; una factura RECHAZADA u OBSERVADA puede volver a enviarse. */
    Respuesta recibirFactura(String cuf, String xml, String hashXml);

    Respuesta consultarEstado(String cuf);

    Respuesta anular(String cuf, Integer codigoMotivo);
}
