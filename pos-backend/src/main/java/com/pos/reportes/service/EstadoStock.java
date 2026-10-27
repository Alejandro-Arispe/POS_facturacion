package com.pos.reportes.service;

/**
 * Regla unica del estado de un stock. Los reportes estaticos, parametrizados, dinamicos y el QBE
 * ("Bajo el minimo") la aplican igual: agotado si no queda stock, bajo si esta en el minimo o por debajo.
 */
public final class EstadoStock {

    public static final String AGOTADO = "agotado";
    public static final String BAJO = "bajo";
    public static final String SOBRE = "sobre";
    public static final String NORMAL = "normal";

    /** Condicion SQL equivalente a {@link #BAJO} o {@link #AGOTADO}, para tablas con alias st (stock). */
    public static final String ALERTA_SQL = "(st.cantidad <= 0 or (st.stock_minimo is not null and st.cantidad <= st.stock_minimo))";

    private EstadoStock() {
    }

    public static String de(Integer cantidad, Integer minimo, Integer maximo) {
        int actual = cantidad == null ? 0 : cantidad;
        if (actual <= 0) {
            return AGOTADO;
        }
        if (minimo != null && actual <= minimo) {
            return BAJO;
        }
        if (maximo != null && actual > maximo) {
            return SOBRE;
        }
        return NORMAL;
    }

    public static boolean esAlerta(String estado) {
        return AGOTADO.equals(estado) || BAJO.equals(estado);
    }
}
