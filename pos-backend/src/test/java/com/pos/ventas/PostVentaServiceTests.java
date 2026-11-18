package com.pos.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.pos.common.exception.ApiException;
import com.pos.common.seed.DemoAccountSeeder;
import com.pos.empresas.domain.Caja;
import com.pos.empresas.domain.CajaSesion;
import com.pos.empresas.domain.MovimientoCaja;
import com.pos.empresas.domain.TipoMovimientoCaja;
import com.pos.usuarios.domain.Usuario;
import com.pos.ventas.dto.VentaDtos;
import com.pos.ventas.service.PostVentaService;
import com.pos.ventas.service.VentaService;
import com.pos.reportes.dto.ReporteDtos;
import com.pos.reportes.service.ReportesService;

import jakarta.persistence.EntityManager;

/** HU-14 (anulacion) y HU-15 (devolucion): stock, caja, saldo a favor y reglas de negocio. */
@ActiveProfiles("seed-demo")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:postventa;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.media-dir=target/seed-test-media", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.config.import=",
        "app.superadmin.email=", "app.superadmin.password=", "app.ai.gemini.api-key=",
        "app.sin-simulado.disponible=true"
})
class PostVentaServiceTests {

    @Autowired DemoAccountSeeder seeder;
    @Autowired VentaService ventas;
    @Autowired PostVentaService postVenta;
    @Autowired ReportesService reportes;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    Usuario owner;
    Integer empresa;
    Integer sucursal;
    Integer sesion;
    Integer producto;
    BigDecimal precio;
    Integer efectivo;
    Integer contado;

    @BeforeEach
    void setUp() {
        String email = "postventa-" + UUID.randomUUID().toString().substring(0, 8) + "@example.invalid";
        owner = seeder.ensureUser(email, "Clave2026!", "Dueño de pruebas", "PV-" + UUID.randomUUID().toString().substring(0, 8));
        empresa = seeder.seed(email).companyId();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()));
        sucursal = jdbc.queryForObject("select min(id_sucursal) from sucursal where id_empresa = ?", Integer.class, empresa);
        // Un producto con stock suficiente en la sucursal.
        producto = jdbc.queryForObject("""
                select min(st.id_producto) from stock st join producto p on p.id_producto = st.id_producto
                where p.id_empresa = ? and st.id_sucursal = ? and st.cantidad >= 10
                """, Integer.class, empresa, sucursal);
        precio = jdbc.queryForObject("select precio from producto where id_producto = ?", BigDecimal.class, producto);
        efectivo = jdbc.queryForObject("select id_metodo_pago from metodo_pago where nombre = 'EFECTIVO'", Integer.class);
        contado = jdbc.queryForObject("select id_tipo_venta from tipo_venta where lower(nombre) = 'contado'", Integer.class);
        sesion = abrirCaja(new BigDecimal("600.00"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anularVentaReponeStockDevuelveDineroYQuedaFueraDelHistorialActivo() {
        int stockAntes = stock();
        BigDecimal esperadoAntes = efectivoEsperado();
        Integer idVenta = vender(3, null, efectivo);
        assertThat(stock()).isEqualTo(stockAntes - 3);

        assertThatThrownBy(() -> postVenta.anular(owner, idVenta, "no", null))
                .isInstanceOf(ApiException.class).hasMessageContaining("motivo");

        var r = postVenta.anular(owner, idVenta, "Error al registrar la venta", null);
        assertThat(r.get("estado")).isEqualTo("ANULADA");
        assertThat(stock()).isEqualTo(stockAntes);
        assertThat(efectivoEsperado()).isEqualByComparingTo(esperadoAntes);
        assertThat(jdbc.queryForObject("select motivo_anulacion from venta where id_venta = ?", String.class, idVenta))
                .isEqualTo("Error al registrar la venta");
        assertThatThrownBy(() -> postVenta.anular(owner, idVenta, "Segundo intento", null))
                .isInstanceOf(ApiException.class).hasMessageContaining("ya está anulada");
    }

    @Test
    void devolucionParcialYTotalConEfectivoYSaldoAFavorQueLuegoSeUsaComoPago() {
        Integer cliente = jdbc.queryForObject("""
                select min(c.id_cliente) from cliente c join categoria_cliente cc on cc.id_categoria_cliente = c.id_categoria_cliente
                where c.id_empresa = ? and cc.descuento_base = 0
                """, Integer.class, empresa);
        int stockAntes = stock();
        Integer idVenta = vender(4, cliente, efectivo);
        BigDecimal esperadoTrasVenta = efectivoEsperado();

        // Parcial en efectivo: sale dinero de la caja y vuelve el stock.
        var parcial = postVenta.devolver(owner, idVenta, List.of(new PostVentaService.LineaDevolucion(producto, 1)),
                "EFECTIVO", "Producto dañado");
        assertThat(parcial.get("tipo")).isEqualTo("PARCIAL");
        assertThat((BigDecimal) parcial.get("monto")).isEqualByComparingTo(precio);
        assertThat(efectivoEsperado()).isEqualByComparingTo(esperadoTrasVenta.subtract(precio));
        assertThat(stock()).isEqualTo(stockAntes - 3);

        // No se puede devolver mas de lo pendiente.
        assertThatThrownBy(() -> postVenta.devolver(owner, idVenta, List.of(new PostVentaService.LineaDevolucion(producto, 4)),
                "EFECTIVO", "Cantidad excesiva")).isInstanceOf(ApiException.class).hasMessageContaining("Solo quedan 3");

        // El resto como saldo a favor: la venta queda DEVUELTA y el cliente con saldo.
        var total = postVenta.devolver(owner, idVenta, List.of(new PostVentaService.LineaDevolucion(producto, 3)),
                "SALDO_A_FAVOR", "El cliente cambió de opinión");
        assertThat(total.get("tipo")).isEqualTo("TOTAL");
        assertThat(jdbc.queryForObject("select estado from venta where id_venta = ?", String.class, idVenta)).isEqualTo("DEVUELTA");
        BigDecimal saldo = saldoAFavor(cliente);
        assertThat(saldo).isEqualByComparingTo(precio.multiply(BigDecimal.valueOf(3)));
        assertThat(stock()).isEqualTo(stockAntes);

        // La siguiente compra se paga con el saldo a favor y no entra dinero a la caja.
        Integer saldoMetodo = jdbc.queryForObject("select id_metodo_pago from metodo_pago where nombre = 'SALDO A FAVOR'", Integer.class);
        BigDecimal esperadoAntesCompra = efectivoEsperado();
        vender(1, cliente, saldoMetodo);
        assertThat(saldoAFavor(cliente)).isEqualByComparingTo(saldo.subtract(precio));
        assertThat(efectivoEsperado()).isEqualByComparingTo(esperadoAntesCompra);
    }

    @Test
    @SuppressWarnings("unchecked")
    void devolucionParcialSeRestaEnVentasNetasDelReporte() {
        Integer idVenta = vender(2, null, efectivo);
        postVenta.devolver(owner, idVenta, List.of(new PostVentaService.LineaDevolucion(producto, 1)), "EFECTIVO", "Producto vencido");
        ReporteDtos.VentasParamRequest filtros = new ReporteDtos.VentasParamRequest();
        filtros.setFechaInicial(java.time.LocalDate.now());
        filtros.setFechaFinal(java.time.LocalDate.now());
        filtros.setIdSucursal(sucursal);
        var reporte = reportes.ventasParametrizado(owner, empresa, filtros);
        var indicadores = (List<java.util.Map<String, Object>>) reporte.get("resumen_gerencial");
        java.util.function.Function<String, Double> valor = clave -> indicadores.stream()
                .filter(i -> clave.equals(i.get("clave"))).map(i -> ((Number) i.get("valor")).doubleValue()).findFirst().orElseThrow();
        assertThat(valor.apply("devoluciones")).isEqualTo(precio.doubleValue());
        assertThat(valor.apply("ventas_netas")).isEqualTo(valor.apply("monto_total") - precio.doubleValue());
    }

    @Test
    void devolucionFueraDelPlazoSeRechaza() {
        Integer idVenta = vender(1, null, efectivo);
        jdbc.update("update venta set fecha = ? where id_venta = ?", LocalDateTime.now().minusDays(8), idVenta);
        assertThatThrownBy(() -> postVenta.devolver(owner, idVenta, List.of(new PostVentaService.LineaDevolucion(producto, 1)),
                "EFECTIVO", "Fuera de plazo")).isInstanceOf(ApiException.class).hasMessageContaining("7 días");
    }

    // ---------------------------------------------------------------------------------------------

    private Integer abrirCaja(BigDecimal inicial) {
        return tx.execute(status -> {
            Caja caja = em.createQuery("select c from Caja c where c.sucursal.idSucursal = :s", Caja.class)
                    .setParameter("s", sucursal).setMaxResults(1).getSingleResult();
            CajaSesion s = new CajaSesion();
            s.setCaja(caja);
            s.setIdUsuario(owner.getIdUsuario());
            s.setFechaApertura(LocalDateTime.now());
            s.setMontoInicial(inicial);
            s.setEstado("Abierto");
            em.persist(s);
            MovimientoCaja apertura = new MovimientoCaja();
            apertura.setCajaSesion(s);
            apertura.setTipoMovimientoCaja(em.createQuery("select t from TipoMovimientoCaja t where t.nombre = 'APERTURA'",
                    TipoMovimientoCaja.class).getSingleResult());
            apertura.setIdMetodoPago(efectivo);
            apertura.setIdUsuario(owner.getIdUsuario());
            apertura.setFecha(LocalDateTime.now());
            apertura.setMonto(inicial);
            apertura.setConcepto("APERTURA " + s.getIdCajaSesion());
            em.persist(apertura);
            return s.getIdCajaSesion();
        });
    }

    private Integer vender(int cantidad, Integer cliente, Integer metodo) {
        BigDecimal total = precio.multiply(BigDecimal.valueOf(cantidad));
        VentaDtos.DetalleCreate detalle = new VentaDtos.DetalleCreate();
        detalle.setIdProducto(producto);
        detalle.setCantidad(cantidad);
        detalle.setPrecioUnitario(precio);
        detalle.setSubtotal(total);
        VentaDtos.PagoCreate pago = new VentaDtos.PagoCreate();
        pago.setIdMetodoPago(metodo);
        pago.setMonto(total);
        VentaDtos.VentaCreate venta = new VentaDtos.VentaCreate();
        venta.setIdTipoVenta(contado);
        venta.setIdCliente(cliente);
        venta.setPagos(List.of(pago));
        venta.setSubtotal(total);
        venta.setDescuentoTotal(BigDecimal.ZERO);
        venta.setTotal(total);
        venta.setDetalles(List.of(detalle));
        return ventas.crearVenta(owner, sesion, venta).getIdVenta();
    }

    private int stock() {
        return jdbc.queryForObject("select cantidad from stock where id_producto = ? and id_sucursal = ?", Integer.class, producto, sucursal);
    }

    private BigDecimal saldoAFavor(Integer cliente) {
        return jdbc.queryForObject("select coalesce(saldo_a_favor, 0) from cliente where id_cliente = ?", BigDecimal.class, cliente);
    }

    /** Efectivo esperado de la sesion: entradas menos salidas en EFECTIVO. */
    private BigDecimal efectivoEsperado() {
        return jdbc.queryForObject("""
                select coalesce(sum(case when t.nombre in ('EGRESO', 'AJUSTE_NEGATIVO') then -m.monto
                                         when t.nombre = 'CIERRE' then 0 else m.monto end), 0)
                from movimiento_caja m join tipo_movimiento_caja t on t.id_tipo_movimiento_caja = m.id_tipo_movimiento_caja
                where m.id_caja_sesion = ? and m.id_metodo_pago = ?
                """, BigDecimal.class, sesion, efectivo);
    }
}
