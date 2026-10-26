package com.pos.empresas;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.common.mail.EmailService;
import com.pos.empresas.domain.Plan;
import com.pos.empresas.dto.*;
import com.pos.empresas.repo.*;
import com.pos.empresas.service.*;
import com.pos.pagos.dto.PagoDtos;
import com.pos.pagos.service.PagoService;
import com.pos.productos.domain.Producto;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.dto.*;
import com.pos.usuarios.service.UsuarioService;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:onboarding;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.config.import=", "app.superadmin.email=", "app.superadmin.password=",
    "app.ai.gemini.api-key=", "app.auth.auto-activate-on-email-fail=false", "app.stripe.secret-key=sk_test_local_fixture",
    "app.stripe.test-only=true", "app.stripe.webhook-secret=whsec_fixture",
    "app.frontend-base-url=https://pos-frontend-si2.duckdns.org"
})
@Transactional
class OnboardingTests {
    static final AtomicBoolean paid = new AtomicBoolean();
    static final AtomicReference<String> form = new AtomicReference<>();
    static final HttpServer stripe = server();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry props) {
        props.add("app.stripe.api-base-url", () -> "http://127.0.0.1:" + stripe.getAddress().getPort());
    }
    static HttpServer server() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/checkout/sessions", exchange -> {
                String json;
                if (exchange.getRequestMethod().equals("POST")) {
                    form.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    json = "{\"id\":\"cs_test_" + UUID.randomUUID() + "\",\"url\":\"https://checkout.stripe.com/test_fixture\"}";
                } else json = "{\"payment_status\":\"" + (paid.get() ? "paid" : "unpaid") + "\",\"payment_intent\":\"pi_test_fixture\"}";
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start(); return server;
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
    @AfterAll static void stopStripe() { stripe.stop(0); }
    @Autowired EntityManager em;
    @Autowired EmpresaService companies;
    @Autowired SucursalService branches;
    @Autowired CajaService cash;
    @Autowired PlanService plans;
    @Autowired PlanRepository planRepository;
    @Autowired PagoService payments;
    @Autowired PlanPolicy policy;
    @Autowired EmpresaAccess access;
    @Autowired UsuarioService users;
    @Autowired HistorialSuscripcionRepository subscriptions;
    @Autowired com.pos.pagos.service.CompraPlanService purchases;
    @MockitoBean EmailService mail;
    Usuario owner;

    @BeforeEach void setup() { paid.set(false); owner = user(); authenticate(owner); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void publishesThreePlansInBobWithoutSalesQuota() {
        var catalog = plans.listar();
        assertThat(catalog).extracting(PlanResponse::nombre).containsExactly("Gratuito", "Básico", "Pro");
        assertThat(catalog).extracting(PlanResponse::moneda).containsOnly("BOB");
        assertThat(catalog).extracting(p -> p.limites().maxUsuarios()).containsExactly(2, 5, 15);
        assertThat(catalog).extracting(PlanResponse::precio).usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("0"), new BigDecimal("99"), new BigDecimal("199"));
    }

    @Test void paidPlanBelongsToAccountBeforeCompanyAndCanBeAssignedAfterPayment() {
        long companiesBefore = em.createQuery("select count(e) from Empresa e", Long.class).getSingleResult();
        var checkout = accountCheckout("Básico", "http://localhost:4200");
        String sent = java.net.URLDecoder.decode(form.get(), StandardCharsets.UTF_8);
        assertThat(sent).contains("success_url=http://localhost:4200/payment/success?session_id={CHECKOUT_SESSION_ID}");
        assertThat(sent).contains("metadata[id_compra_plan]").doesNotContain("metadata[id_empresa]");
        assertThat(em.createQuery("select count(e) from Empresa e", Long.class).getSingleResult()).isEqualTo(companiesBefore);
        assertThat(payments.confirmar(owner, checkout.getSessionId()).getCompra().getEstado()).isEqualTo("pendiente");
        assertThat(purchases.disponibles(owner)).isEmpty();
        paid.set(true);
        var purchase = payments.confirmar(owner, checkout.getSessionId()).getCompra();
        assertThat(purchase.getEstado()).isEqualTo("pagado"); assertThat(purchase.getIdEmpresa()).isNull();
        assertThat(purchases.disponibles(owner)).hasSize(1);
        String unique = UUID.randomUUID().toString().substring(0, 15);
        var company = companies.crear(new EmpresaCreate("Después de pagar", "Demo SRL", unique, unique + "@example.invalid", purchase.getIdCompraPlan())).empresa();
        assertThat(company.suscripcionActiva().planNombre()).isEqualTo("Básico");
        assertThat(purchases.disponibles(owner)).isEmpty();
        assertThat(payments.confirmar(owner, checkout.getSessionId()).getCompra().getIdEmpresa()).isEqualTo(company.idEmpresa());
    }

    @Test void freePlanCanBeSelectedWithoutCompanyAndIsNotDuplicatedOnReload() {
        var first = accountCheckout("Gratuito", "http://localhost:4200");
        var second = accountCheckout("Gratuito", "http://localhost:4200");
        assertThat(second.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(first.getCheckoutUrl()).startsWith("http://localhost:4200/payment/success");
        assertThat(payments.confirmar(owner, first.getSessionId()).getCompra().isGratuito()).isTrue();
        assertThat(purchases.disponibles(owner)).hasSize(1);
    }

    @Test void returningAfterStripePaymentRecoversPurchaseWithoutPayingAgain() {
        var first = accountCheckout("Básico", "http://localhost:4200");
        paid.set(true); // El usuario pagó, pero cerró Stripe antes de regresar al frontend.
        var recovered = accountCheckout("Básico", "http://localhost:4200");
        assertThat(recovered.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(recovered.getCheckoutUrl()).startsWith("http://localhost:4200/payment/success");
        assertThat(purchases.disponibles(owner)).hasSize(1);
    }

    @Test void anotherAccountCannotConfirmOrConsumeThePurchasedPlan() {
        var checkout = accountCheckout("Gratuito", "http://localhost:4200");
        var purchase = payments.confirmar(owner, checkout.getSessionId()).getCompra();
        Usuario other = user(); authenticate(other);
        assertThat(purchases.disponibles(other)).isEmpty();
        assertThatThrownBy(() -> payments.confirmar(other, checkout.getSessionId())).isInstanceOf(ApiException.class);
        String unique = UUID.randomUUID().toString().substring(0, 15);
        assertThatThrownBy(() -> companies.crear(new EmpresaCreate("Otra cuenta", "Demo", unique, unique + "@example.invalid", purchase.getIdCompraPlan())))
                .isInstanceOf(ApiException.class);
    }

    @Test void rejectsUntrustedReturnUrlsAndRelativeUrlsBeforeCallingStripe() {
        form.set(null);
        assertThatThrownBy(() -> accountCheckout("Básico", "https://untrusted.example")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> accountCheckout("Básico", "/payment/success")).isInstanceOf(ApiException.class);
        assertThat(form.get()).isNull();
    }

    @Test void aPurchaseCannotBeAssignedToMoreThanOneCompany() {
        var checkout = accountCheckout("Gratuito", "http://localhost:4200");
        int purchaseId = payments.confirmar(owner, checkout.getSessionId()).getCompra().getIdCompraPlan();
        String unique = UUID.randomUUID().toString().substring(0, 15);
        companies.crear(new EmpresaCreate("Primera", "Demo", unique, unique + "@example.invalid", purchaseId));
        String another = UUID.randomUUID().toString().substring(0, 15);
        assertThatThrownBy(() -> companies.crear(new EmpresaCreate("Segunda", "Demo", another, another + "@example.invalid", purchaseId)))
                .isInstanceOf(ApiException.class).hasMessageContaining("disponible");
    }

    private PagoDtos.CheckoutResponse accountCheckout(String name, String origin) {
        var request = new PagoDtos.CheckoutRequest(); request.setIdPlan(planRepository.findByNombreIgnoreCase(name).orElseThrow().getIdPlan());
        request.setFrontendOrigin(origin); return payments.checkout(owner, request);
    }

    @Test void pendingCompanyCannotOperateAndFreePlanRequiresNoStripe() {
        int id = company();
        assertThatThrownBy(() -> access.requireEmpresa(owner, id)).isInstanceOf(ApiException.class).hasMessageContaining("Activa un plan");
        assertThat(companies.obtener(id).suscripcionActiva()).isNull();
        var checkout = checkout(id, "Gratuito");
        assertThat(checkout.getSessionId()).startsWith("free_");
        var result = payments.confirmar(owner, checkout.getSessionId());
        assertThat(result.getSuscripcion().getEstado()).isEqualTo("activo");
        assertThat(result.getSuscripcion().getFechaFin()).isNull();
        assertThat(companies.obtener(id).suscripcionActiva().planNombre()).isEqualTo("Gratuito");
        assertThat(access.requireEmpresa(owner, id).getIdEmpresa()).isEqualTo(id);
    }

    @Test void stripeUsesBobAndPendingPaymentDoesNotGrantAccessAndConfirmationIsIdempotent() {
        int id = company();
        var checkout = checkout(id, "Básico");
        assertThat(form.get()).contains("currency]=bob", "unit_amount]=9900", "mode=payment");
        assertThat(payments.confirmar(owner, checkout.getSessionId()).getSuscripcion().getEstado()).isEqualTo("pendiente");
        assertThatThrownBy(() -> access.requireEmpresa(owner, id)).isInstanceOf(ApiException.class);
        paid.set(true);
        var first = payments.confirmar(owner, checkout.getSessionId()).getSuscripcion();
        assertThat(first.getEstado()).isEqualTo("activo");
        assertThat(first.getFechaFin()).isEqualTo(LocalDate.now().plusDays(30));
        var second = payments.confirmar(owner, checkout.getSessionId()).getSuscripcion();
        assertThat(second.getFechaFin()).isEqualTo(first.getFechaFin());
        assertThat(subscriptions.findActivasPorEmpresa(id, LocalDate.now())).hasSize(1);
    }

    @Test void cannotConfirmAnotherCompanyPayment() {
        int id = company();
        var checkout = checkout(id, "Básico");
        Usuario other = user(); authenticate(other);
        assertThatThrownBy(() -> payments.confirmar(other, checkout.getSessionId())).isInstanceOf(ApiException.class);
    }

    @Test void demonstrationRejectsProductionStripeKeysBeforeContactingStripe() throws Exception {
        int id = company();
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(payments);
        org.springframework.test.util.ReflectionTestUtils.setField(target, "stripeSecret", "sk_live_not_a_real_key");
        try {
            form.set(null);
            assertThatThrownBy(() -> checkout(id, "Básico")).isInstanceOf(ApiException.class).hasMessageContaining("claves de prueba");
            assertThat(form.get()).isNull();
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(target, "stripeSecret", "sk_test_local_fixture");
        }
    }

    @Test void freePlanLimitsBranchesCashProductsAndDistinctStaffIncludingOwner() {
        int id = company(); checkout(id, "Gratuito");
        var branch = branches.crear(id, new SucursalCreate("Centro", "Calle 1", "70000000", "La Paz"));
        assertThatThrownBy(() -> branches.crear(id, new SucursalCreate("Otra", "Calle 2", "70000001", "La Paz")))
                .isInstanceOf(ApiException.class).hasMessageContaining("limite de sucursales");
        cash.crear(branch.idSucursal(), new CajaCreate("Principal", "C1"));
        assertThatThrownBy(() -> cash.crear(branch.idSucursal(), new CajaCreate("Otra", "C2")))
                .isInstanceOf(ApiException.class).hasMessageContaining("cajas activas");
        for (int i = 0; i < 100; i++) {
            Producto p = new Producto(); p.setIdEmpresa(id); p.setNombre("Producto " + i); p.setUnidadMedida("Unidad"); em.persist(p);
        }
        em.flush();
        assertThatThrownBy(() -> policy.requireProductCapacity(id)).isInstanceOf(ApiException.class).hasMessageContaining("100");
        Usuario second = user();
        Rol employee = em.createQuery("select r from Rol r where r.nombre = 'EMPLEADO'", Rol.class).getSingleResult();
        UsuarioRol relation = new UsuarioRol(); relation.setUsuario(second); relation.setRol(employee); relation.setIdEmpresa(id);
        relation.setIdSucursal(branch.idSucursal()); relation.setFecha(LocalDate.now()); relation.setActivo(true); em.persist(relation); em.flush();
        Usuario third = user();
        assertThatThrownBy(() -> branches.editarPersonal(id, new EditarPersonalCreate(third.getEmail(), List.of(branch.idSucursal()), employee.getIdRol(), true)))
                .isInstanceOf(ApiException.class).hasMessageContaining("limite de usuarios");
    }

    @Test void welcomeEmailFollowsVerificationAndNeverReceivesPassword() {
        when(mail.sendVerificationEmail(anyString(), anyString(), anyString())).thenReturn(true);
        String email = UUID.randomUUID() + "@example.invalid";
        var response = users.registrar(new UsuarioRegister(email, "Demo-secret-123", "Prueba", LocalDate.of(2000, 1, 1), "F", "70000000", UUID.randomUUID().toString().substring(0, 20)));
        assertThat(response.activo()).isFalse();
        verify(mail, never()).sendWelcomeEmail(eq(email), anyString());
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerificationEmail(eq(email), eq("Prueba"), code.capture());
        assertThat(users.verificarCodigo(new UsuarioVerifyCode(email, code.getValue())).activo()).isTrue();
        verify(mail).sendWelcomeEmail(email, "Prueba");
    }

    @Test void upgradeReplacesFreeSubscriptionAndFeaturesFollowPlan() {
        int id = company(); checkout(id, "Gratuito");
        assertThatThrownBy(() -> access.requirePlanFeature(owner, id, "pedidos")).isInstanceOf(ApiException.class);
        var paidCheckout = checkout(id, "Pro"); paid.set(true); payments.confirmar(owner, paidCheckout.getSessionId());
        assertThat(subscriptions.findActivasPorEmpresa(id, LocalDate.now())).hasSize(1);
        assertThatCode(() -> access.requirePlanFeature(owner, id, "pedidos")).doesNotThrowAnyException();
    }

    @Test void repeatedWebhookFromReplacedPlanCannotUndoAnUpgrade() throws Exception {
        int id = company(); paid.set(true);
        var basic = checkout(id, "Básico"); payments.confirmar(owner, basic.getSessionId());
        var pro = checkout(id, "Pro"); payments.confirmar(owner, pro.getSessionId());
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\""
                + basic.getSessionId() + "\",\"payment_status\":\"paid\"}}}";
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("whsec_fixture".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        payments.webhook(payload, "t=" + timestamp + ",v1=" + signature);
        var active = subscriptions.findActivasPorEmpresa(id, LocalDate.now());
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getPlan().getNombre()).isEqualTo("Pro");
    }

    private int company() {
        String unique = UUID.randomUUID().toString().substring(0, 15);
        return companies.crear(new EmpresaCreate("Demo", "Demo SRL", unique, unique + "@example.invalid")).empresa().idEmpresa();
    }
    private PagoDtos.CheckoutResponse checkout(int company, String name) {
        Plan plan = planRepository.findByNombreIgnoreCase(name).orElseThrow();
        var request = new PagoDtos.CheckoutRequest(); request.setIdEmpresa(company); request.setIdPlan(plan.getIdPlan());
        return payments.checkout(owner, request);
    }
    private Usuario user() {
        String unique = UUID.randomUUID().toString().substring(0, 20);
        Persona p = new Persona(); p.setNombreCompleto("Demo"); p.setFechaNacimiento(LocalDate.of(2000, 1, 1));
        p.setGenero("F"); p.setTelefono("70000000"); p.setDocumento(unique); em.persist(p);
        Usuario u = new Usuario(); u.setPersona(p); u.setFechaCreacion(LocalDate.now()); u.setEmail(unique + "@example.invalid");
        u.setContrasena("unused"); u.setActivo(true); em.persist(u); em.flush(); return u;
    }
    private void authenticate(Usuario u) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of())); }
}
