package com.pos.common.access;

import java.util.*;

/** Historical mapping used ONLY by the one-time migration and catalogue seed. */
public final class PermissionCodes {
    private PermissionCodes() {
    }
    public static final Map<String, String> ALIASES;
    static {
        Map<String,String> codes = new LinkedHashMap<>();
        for (String line : """
            EMPRESA_EDITAR company:update
            SUCURSAL_VER branch:read
            SUCURSAL_CREAR branch:create
            SUCURSAL_EDITAR branch:update
            USUARIO_VER user:read
            USUARIO_CREAR user:create
            USUARIO_EDITAR user:update
            ROL_VER role:read
            ROL_CREAR role:create
            ROL_EDITAR role:update
            CLIENTE_VER client:read
            CLIENTE_CREAR client:create
            CLIENTE_EDITAR client:update
            CATEGORIA_VER client_category:read
            CATEGORIA_CREAR client_category:create
            CATEGORIA_EDITAR client_category:update
            PRODUCTO_VER product:read
            PRODUCTO_CREAR product:create
            PRODUCTO_EDITAR product:update
            STOCK_VER inventory:read
            STOCK_CONFIGURAR inventory:configure
            MOVIMIENTO_VER inventory_movement:read
            MOVIMIENTO_REGISTRAR inventory_movement:create
            ALERTA_VER inventory_alert:read
            CAJA_VER cash_register:read
            CAJA_CREAR cash_register:create
            CAJA_EDITAR cash_register:update
            CAJA_ABRIR cash_register:open
            CAJA_CERRAR cash_register:close
            PEDIDO_VER order:read
            PEDIDO_GESTIONAR order:manage
            VENTA_VER sale:read
            VENTA_CREAR sale:create
            VENTA_ANULAR sale:void
            VENTA_DESCUENTO sale:discount
            FACTURA_EMITIR invoice:issue
            FACTURA_REIMPRIMIR invoice:reprint
            REPORTE_GENERAR report:generate
            REPORTE_EXPORTAR report:export
            DASHBOARD_VER dashboard:read
            BITACORA_VER audit:read
            """.strip().split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            codes.put(parts[0], parts[1]);
        }
        ALIASES = Collections.unmodifiableMap(codes);
    }
    public static boolean isCanonical(String code) {
        return code != null && code.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*(?::[a-z][a-z0-9_]*)?");
    }
    public static String canonical(String code) {
        if (code == null) return "";
        if (ALIASES.containsKey(code)) return ALIASES.get(code);
        return isCanonical(code) ? code : "";
    }
}
