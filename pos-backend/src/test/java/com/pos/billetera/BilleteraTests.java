package com.pos.billetera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.pos.billetera.domain.CobroBilletera;
import com.pos.billetera.repo.CobroBilleteraRepository;
import com.pos.billetera.service.BilleteraClient;
import com.pos.billetera.service.BilleteraService;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;

/** Firma de webhooks y reglas para usar un cobro QR de la billetera en una venta. */
class BilleteraTests {

    private static final String SECRETO = "whsec_prueba";
    private final ObjectMapper json = new ObjectMapper();
    private final BilleteraClient client = new BilleteraClient(json, "https://billetera.test/api", "sk_test", SECRETO, "");

    private static String firma(String secreto, long t, String cuerpo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void webhookConFirmaValidaSeAceptaYConCuerpoAlteradoOVencidoSeRechaza() throws Exception {
        String cuerpo = "{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"abc\",\"status\":\"PAID\"}}}";
        long ahora = Instant.now().getEpochSecond();
        assertThat(client.verificarWebhook(cuerpo, firma(SECRETO, ahora, cuerpo)).path("type").asText())
                .isEqualTo("payment_intent.succeeded");
        assertThatThrownBy(() -> client.verificarWebhook(cuerpo.replace("PAID", "PAGADO"), firma(SECRETO, ahora, cuerpo)))
                .isInstanceOf(ApiException.class).hasMessageContaining("inválida");
        assertThatThrownBy(() -> client.verificarWebhook(cuerpo, firma("otro_secreto", ahora, cuerpo)))
                .isInstanceOf(ApiException.class).hasMessageContaining("inválida");
        assertThatThrownBy(() -> client.verificarWebhook(cuerpo, firma(SECRETO, ahora - 600, cuerpo)))
                .isInstanceOf(ApiException.class).hasMessageContaining("vencido");
        assertThatThrownBy(() -> client.verificarWebhook(cuerpo, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void sinClavesLaBilleteraQuedaDeshabilitada() {
        BilleteraClient vacio = new BilleteraClient(json, "", "", "", "");
        assertThat(vacio.qrHabilitado()).isFalse();
        assertThat(vacio.tarjetaHabilitada()).isFalse();
        assertThat(client.qrHabilitado()).isTrue();
    }

    @Test
    void unCobroPagadoSeUsaUnaSolaVezPorElMismoMontoYEmpresa() {
        CobroBilleteraRepository repo = mock(CobroBilleteraRepository.class);
        BilleteraService service = new BilleteraService(client, repo, mock(EmpresaAccess.class));
        CobroBilletera cobro = new CobroBilletera();
        cobro.setIdCobro("c1");
        cobro.setIdEmpresa(1);
        cobro.setMonto(new BigDecimal("150.00"));
        cobro.setEstado("PAID");
        cobro.setVence(LocalDateTime.now().plusMinutes(5));
        when(repo.findById("c1")).thenReturn(Optional.of(cobro));

        assertThatThrownBy(() -> service.consumirParaVenta("c1", new BigDecimal("140.00"), 1, 10))
                .isInstanceOf(ApiException.class).hasMessageContaining("monto");
        assertThatThrownBy(() -> service.consumirParaVenta("c1", new BigDecimal("150.00"), 2, 10))
                .isInstanceOf(ApiException.class).hasMessageContaining("otra empresa");
        service.consumirParaVenta("c1", new BigDecimal("150.00"), 1, 10);
        assertThat(cobro.getIdVenta()).isEqualTo(10);
        assertThatThrownBy(() -> service.consumirParaVenta("c1", new BigDecimal("150.00"), 1, 11))
                .isInstanceOf(ApiException.class).hasMessageContaining("ya se usó");
    }
}
