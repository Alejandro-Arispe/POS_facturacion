package com.pos.siat.sin;

/** El SIN no responde (caída simulada): la factura debe quedar en contingencia. */
public class SinNoDisponibleException extends RuntimeException {

    public SinNoDisponibleException() {
        super("El SIN no responde.");
    }
}
