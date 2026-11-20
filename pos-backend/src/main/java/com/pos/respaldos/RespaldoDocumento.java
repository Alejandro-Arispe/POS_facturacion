package com.pos.respaldos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Foto JSON de una empresa o sucursal.
 * Permite respaldar toda la empresa (idSucursal = null) o una sucursal específica.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespaldoDocumento(
        Integer idEmpresa,
        Integer idSucursal,
        String nombreSucursal,
        String alcance,
        LocalDateTime fecha,
        boolean incluyeVentas,
        String mensaje,
        List<String> tablas,
        EmpresaDatos empresa,
        List<SucursalDatos> sucursales,
        List<CategoriaDatos> categorias,
        List<SubcategoriaDatos> subcategorias,
        List<ProductoDatos> productos,
        List<RolDatos> roles,
        List<RolPermisoDatos> rolPermisos,
        ConfiguracionDatos configuracionSistema,
        List<HistorialDatos> historialSuscripcion,
        VentasHistorial ventasHistorial) {

    /** Las ventas del periodo se incluyen como copia de consulta (no se restauran). */
    public static final boolean INCLUYE_VENTAS = true;

    public static final String MENSAJE_VENTAS = "Las ventas del período (detalle, pagos, facturas y cierres de caja) "
            + "se guardan como copia de consulta: puedes verlas en el JSON o en el Excel del respaldo. "
            + "Al restaurar no se borra ni se modifica ninguna venta o factura, porque son historial y documentos fiscales. "
            + "La restauración reemplaza catálogo, sucursales, configuración, roles personalizados y suscripción de esta empresa.";

    /**
     * Ventas del periodo [desde, hasta] (fechas de venta, ambos dias incluidos). Solo lectura:
     * la restauracion ignora este bloque. Cada fila es un mapa columna -> valor para que el Excel
     * y el JSON muestren nombres legibles (sucursal, producto, metodo de pago) sin depender de ids.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VentasHistorial(
            LocalDate desde,
            LocalDate hasta,
            int cantidadVentas,
            BigDecimal totalVendido,
            List<java.util.Map<String, Object>> ventas,
            List<java.util.Map<String, Object>> detalles,
            List<java.util.Map<String, Object>> pagos,
            List<java.util.Map<String, Object>> facturas,
            List<java.util.Map<String, Object>> sesionesCaja,
            List<java.util.Map<String, Object>> cierresCaja) {
    }

    public static final List<String> TABLAS = List.of(
            "empresa",
            "sucursal",
            "categoria_producto",
            "subcategoria_producto",
            "producto",
            "rol",
            "rol_permiso",
            "configuracion_sistema",
            "historial_suscripcion");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmpresaDatos(
            Integer idEmpresa,
            String nombre,
            String razonSocial,
            String nit,
            String correo,
            LocalDate fechaCreacion,
            Boolean activo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SucursalDatos(
            Integer idSucursal,
            Integer idEmpresa,
            String nombre,
            String direccion,
            String telefono,
            String ciudad,
            LocalDate fechaRegistro,
            Boolean activo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoriaDatos(
            Integer idCategoriaProducto,
            Integer idEmpresa,
            String nombre,
            String descripcion,
            Boolean activo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubcategoriaDatos(
            Integer idSubcategoria,
            Integer idCategoriaProducto,
            String nombre,
            String descripcion,
            Boolean activo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductoDatos(
            Integer idProducto,
            Integer idEmpresa,
            Integer idSubcategoria,
            String nombre,
            String codigoBarra,
            String descripcion,
            String unidadMedida,
            BigDecimal precio,
            BigDecimal costo,
            String imagen,
            Boolean activo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RolDatos(
            Integer idRol,
            Integer idEmpresa,
            String nombre,
            String tipo,
            String descripcion,
            Boolean activo) {
    }

    /** Solo el enlace al permiso global. No se copian filas de permiso, módulo ni plan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RolPermisoDatos(
            Integer idRolPermiso,
            Integer idRol,
            Integer idPermiso,
            Boolean activo, Boolean puedeDelegar) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfiguracionDatos(
            Integer idConfiguracion,
            Integer idEmpresa,
            String tema,
            String idioma,
            String zonaHoraria,
            String moneda,
            Boolean activarNotificacionesPush,
            Boolean activarSonido,
            Boolean activarVibracion,
            Boolean confirmarAntesDeEliminar,
            Boolean cerrarSesionPorInactividad,
            Integer minutosInactividad,
            Boolean imprimirAutomaticamente,
            Integer numeroCopias,
            String tamanoTicket,
            String colorPrimario) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistorialDatos(
            Integer idHistorialSuscripcion,
            Integer idPlan,
            Integer idEmpresa,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String estado,
            String stripeSessionId,
            String stripePaymentIntentId,
            String stripePaymentStatus) {
    }
}
