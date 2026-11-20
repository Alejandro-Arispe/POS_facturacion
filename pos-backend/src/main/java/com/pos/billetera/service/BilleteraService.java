package com.pos.billetera.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import com.pos.billetera.domain.CobroBilletera;
import com.pos.billetera.repo.CobroBilleteraRepository;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.Usuario;

/**
 * Cobros QR con la Billetera: crear el cobro y mostrar su QR en caja, seguir su estado (webhook firmado y,
 * como respaldo, consulta a la billetera) y exigir que este PAGADO antes de registrar la venta.
 */
@Service
public class BilleteraService {

    private static final ZoneId BOLIVIA = ZoneId.of("America/La_Paz");
    public static final String PAGADO = "PAID";
    private static final List<String> FINALES = List.of("PAID", "EXPIRED", "CANCELLED");

    private final BilleteraClient client;
    private final CobroBilleteraRepository cobros;
    private final EmpresaAccess empresaAccess;

    public BilleteraService(BilleteraClient client, CobroBilleteraRepository cobros, EmpresaAccess empresaAccess) {
        this.client = client;
        this.cobros = cobros;
        this.empresaAccess = empresaAccess;
    }

    public boolean qrHabilitado() {
        return client.qrHabilitado();
    }

    /** Crea el cobro en la billetera y devuelve el QR para mostrar en caja. */
    @Transactional
    public Map<String, Object> crearCobro(Usuario usuario, Integer idEmpresa, Integer idSucursal, BigDecimal monto,
            String descripcion) {
        if (idEmpresa == null) throw ApiException.badRequest("Indica la empresa.");
        if (idSucursal == null) empresaAccess.requirePermiso(usuario, idEmpresa, "sale:create");
        else empresaAccess.requirePermiso(usuario, idEmpresa, idSucursal, "sale:create");
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2) throw ApiException.badRequest("Monto inválido.");
        String referencia = "POS-" + idEmpresa + "-" + System.currentTimeMillis();
        JsonNode intent = client.crearCobro(monto, referencia, descripcion, List.of());

        CobroBilletera c = new CobroBilletera();
        c.setIdCobro(intent.path("id").asText());
        c.setReferencia(referencia);
        c.setIdEmpresa(idEmpresa);
        c.setIdSucursal(idSucursal);
        c.setIdUsuario(usuario.getIdUsuario());
        c.setMonto(new BigDecimal(intent.path("amount").asText(monto.toPlainString())));
        c.setMoneda(intent.path("currency").asText("BOB"));
        c.setEstado(intent.path("status").asText("CREATED"));
        c.setVence(fecha(intent.path("expiresAt").asText()));
        cobros.save(c);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", c.getIdCobro());
        result.put("estado", c.getEstado());
        result.put("monto", c.getMonto());
        result.put("referencia", referencia);
        result.put("vence", intent.path("expiresAt").asText());
        result.put("qr_imagen", intent.path("qr").path("image").asText());
        result.put("qr_texto", intent.path("qr").path("payload").asText());
        return result;
    }

    /**
     * Estado del cobro. Normalmente lo actualiza el webhook; si todavia figura pendiente se consulta a la
     * billetera (por si el webhook no llego: firewall, reinicio del servidor, etc.).
     */
    @Transactional
    public Map<String, Object> estado(Usuario usuario, String idCobro) {
        CobroBilletera c = cobroPropio(usuario, idCobro);
        if (!FINALES.contains(c.getEstado())) aplicar(c, client.obtenerCobro(idCobro));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", c.getIdCobro());
        result.put("estado", c.getEstado());
        result.put("monto", c.getMonto());
        result.put("pagado_por", c.getPagadoPor() == null ? "" : c.getPagadoPor());
        return result;
    }

    @Transactional
    public void cancelar(Usuario usuario, String idCobro) {
        CobroBilletera c = cobroPropio(usuario, idCobro);
        if (FINALES.contains(c.getEstado())) return;
        aplicar(c, client.cancelarCobro(idCobro));
    }

    /** Webhook de la billetera (firma ya verificada). Idempotente: aplicar el mismo evento dos veces no cambia nada. */
    @Transactional
    public void webhook(JsonNode evento) {
        JsonNode intent = evento.path("data").path("object");
        cobros.findById(intent.path("id").asText()).ifPresent(c -> aplicar(c, intent));
    }

    /**
     * Al registrar una venta con pago QR: el cobro debe estar PAGADO, ser de la misma empresa, por el mismo
     * monto y no haberse usado en otra venta. Lo marca como usado (dentro de la transaccion de la venta).
     */
    @Transactional
    public void consumirParaVenta(String idCobro, BigDecimal monto, Integer idEmpresa, Integer idVenta) {
        CobroBilletera c = cobros.findById(idCobro).orElseThrow(() -> ApiException.badRequest("El cobro QR no existe."));
        if (!PAGADO.equals(c.getEstado())) aplicar(c, client.obtenerCobro(idCobro));
        if (!PAGADO.equals(c.getEstado())) throw ApiException.badRequest("El cobro QR todavía no fue pagado.");
        if (!c.getIdEmpresa().equals(idEmpresa)) throw ApiException.badRequest("El cobro QR es de otra empresa.");
        if (c.getMonto().compareTo(monto) != 0) throw ApiException.badRequest("El monto del pago QR no coincide con el cobro.");
        if (c.getIdVenta() != null) throw ApiException.badRequest("Ese cobro QR ya se usó en otra venta.");
        c.setIdVenta(idVenta);
        cobros.save(c);
    }

    private CobroBilletera cobroPropio(Usuario usuario, String idCobro) {
        CobroBilletera c = cobros.findById(idCobro).orElseThrow(() -> ApiException.notFound("Cobro no encontrado."));
        empresaAccess.requireEmpresa(usuario, c.getIdEmpresa());
        return c;
    }

    private void aplicar(CobroBilletera c, JsonNode intent) {
        c.setEstado(intent.path("status").asText(c.getEstado()));
        if (intent.hasNonNull("payer")) c.setPagadoPor(intent.path("payer").path("name").asText(null));
        if (intent.hasNonNull("paidAt")) c.setFechaPago(fecha(intent.path("paidAt").asText()));
        cobros.save(c);
    }

    private static LocalDateTime fecha(String iso) {
        try {
            return LocalDateTime.ofInstant(Instant.parse(iso), BOLIVIA);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
