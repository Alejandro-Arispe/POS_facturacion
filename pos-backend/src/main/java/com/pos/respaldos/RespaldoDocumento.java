package com.pos.respaldos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Foto JSON de una sola empresa. No incluye ventas: cuelgan de caja_sesion
 * (sucursal, caja, sesión, venta, detalle, factura y pagos).
 * Producto incluye costo porque la columna existe en la entidad.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespaldoDocumento(
        Integer idEmpresa,
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
        List<HistorialDatos> historialSuscripcion) {

    public static final boolean INCLUYE_VENTAS = false;

    public static final String MENSAJE_VENTAS = "Este respaldo no incluye ventas. "
            + "Cuelgan de caja_sesion (sucursal, caja, sesión, venta y detalle), además de facturas y pagos. "
            + "Restaurarlas obligaría a borrar ese historial. "
            + "No se hace, para no borrar ventas ni datos de otras empresas. "
            + "La copia sí reemplaza catálogo, sucursales, configuración, roles personalizados y suscripción de esta empresa.";

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
