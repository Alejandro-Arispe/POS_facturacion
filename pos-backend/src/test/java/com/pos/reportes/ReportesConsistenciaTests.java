package com.pos.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import com.pos.common.security.PasswordService;
import com.pos.common.seed.DemoAccountSeeder;
import com.pos.reportes.dto.ReporteDtos;
import com.pos.reportes.qbe.QbeDtos;
import com.pos.reportes.qbe.QbeService;
import com.pos.reportes.service.ReportesService;
import com.pos.usuarios.domain.Persona;
import com.pos.usuarios.domain.Usuario;

import jakarta.persistence.EntityManager;

/**
 * Genera los mismos datos por las distintas vias (estatico, parametrizado, dinamico y QBE) sobre la cuenta
 * demo y comprueba que los totales coinciden entre si y con la base de datos.
 */
@ActiveProfiles("seed-demo")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reportes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.config.import=",
        "app.superadmin.email=", "app.superadmin.password=", "app.ai.gemini.api-key="
})
class ReportesConsistenciaTests {

    @Autowired DemoAccountSeeder seeder;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired PasswordService passwords;
    @Autowired ReportesService reportes;
    @Autowired QbeService qbe;

    Usuario owner;
    Integer empresa;
    final LocalDate hoy = LocalDate.now();
    /** La cuenta demo tiene ventas y cajas en los 30 dias anteriores a hoy (no hoy). */
    final LocalDate diaConDatos = hoy.minusDays(5);

    @BeforeEach
    void seed() {
        String email = createOwner();
        empresa = seeder.seed(email).companyId();
        owner = transactions.execute(status -> em.createQuery("select u from Usuario u where u.email = :e", Usuario.class)
                .setParameter("e", email).getSingleResult());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ventasCoincidenEntreParametrizadoDinamicoYBaseDeDatos() {
        LocalDate desde = hoy.minusDays(29);
        ReporteDtos.VentasParamRequest filtros = new ReporteDtos.VentasParamRequest();
        filtros.setFechaInicial(desde);
        filtros.setFechaFinal(hoy);
        Map<String, Object> param = reportes.ventasParametrizado(owner, empresa, filtros);

        ReporteDtos.RespuestaReporte resumen = reportes.ejecutar(owner, empresa, "resumen de ventas");
        ReporteDtos.RespuestaReporte metodos = reportes.ejecutar(owner, empresa, "ventas por metodo de pago");
        ReporteDtos.RespuestaReporte detalle = reportes.ejecutar(owner, empresa, "detalle de ventas");
        ReporteDtos.RespuestaReporte porSucursal = reportes.ejecutar(owner, empresa, "ventas por sucursal");

        double totalParam = num(indicador(param, "monto_total"));
        long cantidadParam = (long) num(indicador(param, "cantidad_ventas"));
        assertThat(cantidadParam).isPositive();
        assertThat(num(resumen.getAgregados().get("total_ventas"))).isEqualTo(totalParam);
        assertThat((long) num(resumen.getAgregados().get("cantidad_ventas"))).isEqualTo(cantidadParam);
        assertThat(num(metodos.getAgregados().get("total_ventas"))).isEqualTo(totalParam);
        assertThat(num(detalle.getAgregados().get("total_ventas"))).isEqualTo(totalParam);
        assertThat(num(porSucursal.getAgregados().get("total_ventas"))).isEqualTo(totalParam);

        double enBase = jdbc.queryForObject("""
                select coalesce(sum(v.total), 0) from venta v
                join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion
                join caja ca on ca.id_caja = cs.id_caja
                join sucursal s on s.id_sucursal = ca.id_sucursal
                where s.id_empresa = ? and upper(v.estado) not in ('ANULADA', 'CANCELADA')
                  and v.fecha >= ? and v.fecha < ?
                """, Double.class, empresa, desde.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        assertThat(totalParam).isEqualTo(Math.round(enBase * 100) / 100.0);

        // Unidades: la suma por fila es igual al indicador del resumen.
        List<Map<String, Object>> filas = detalle(param);
        assertThat(filas.stream().mapToDouble(f -> num(f.get("productos"))).sum())
                .isEqualTo(num(indicador(param, "productos_vendidos")));
        assertThat(filas).allSatisfy(f -> assertThat(f.get("personal")).isInstanceOf(String.class));
    }

    @Test
    void resumenEstaticoDelDiaCoincideConParametrizadoDelDia() {
        Map<String, Object> estatico = reportes.resumenVentas(owner, empresa, diaConDatos);
        Map<String, Object> detalleDia = reportes.detalleVentas(owner, empresa, diaConDatos);
        ReporteDtos.VentasParamRequest filtros = new ReporteDtos.VentasParamRequest();
        filtros.setFechaInicial(diaConDatos);
        filtros.setFechaFinal(diaConDatos);
        Map<String, Object> param = reportes.ventasParametrizado(owner, empresa, filtros);

        Map<String, Object> total = map(estatico.get("total_empresa"));
        assertThat(num(total.get("total_ventas"))).isPositive();
        assertThat(num(total.get("monto_vendido"))).isEqualTo(num(indicador(param, "monto_total")));
        assertThat(num(total.get("total_ventas"))).isEqualTo(num(indicador(param, "cantidad_ventas")));
        assertThat(num(total.get("productos_vendidos"))).isEqualTo(num(indicador(param, "productos_vendidos")));
        assertThat(num(map(detalleDia.get("total_empresa")).get("total_vendido"))).isEqualTo(num(total.get("monto_vendido")));
    }

    @Test
    void stockUsaLaMismaReglaEnTodosLosReportes() {
        Map<String, Object> estatico = map(reportes.estadoInventario(owner, empresa).get("total_empresa"));
        Map<String, Object> param = reportes.inventarioParametrizado(owner, empresa, new ReporteDtos.InventarioParamRequest());
        ReporteDtos.RespuestaReporte alertas = reportes.ejecutar(owner, empresa, "alertas de stock");

        long enAlerta = (long) (num(estatico.get("productos_bajo_stock")) + num(estatico.get("productos_agotados")));
        assertThat(num(indicador(param, "productos_bajo_stock"))).isEqualTo(num(estatico.get("productos_bajo_stock")));
        assertThat(num(indicador(param, "productos_agotados"))).isEqualTo(num(estatico.get("productos_agotados")));
        assertThat(num(indicador(param, "stock_disponible"))).isEqualTo(num(estatico.get("stock_disponible")));
        assertThat(alertas.getFilas()).hasSize((int) enAlerta);

        QbeDtos.Resultado qbeAlertas = qbe.ejecutar(empresa, new QbeDtos.Consulta("inventario", List.of("producto"),
                List.of(new QbeDtos.Condicion("bajo_minimo", "igual", "true", null)), null, null, null, 1000));
        assertThat((long) qbeAlertas.filas().size()).isEqualTo(enAlerta);
    }

    @Test
    void movimientosDeInventarioRespetanElPeriodoYCoincidenEntreVias() {
        LocalDate desde = hoy.minusDays(6);
        Map<String, Object> estatico = reportes.movimientosInventario(owner, empresa, null, null);
        assertThat(estatico.get("fecha_inicio")).isEqualTo(desde.toString());
        ReporteDtos.InventarioParamRequest filtros = new ReporteDtos.InventarioParamRequest();
        filtros.setFechaInicial(desde);
        filtros.setFechaFinal(hoy);
        Map<String, Object> param = reportes.inventarioParametrizado(owner, empresa, filtros);

        Map<String, Object> total = map(estatico.get("total_empresa"));
        assertThat(num(indicador(param, "total_entradas"))).isEqualTo(num(total.get("total_entradas")));
        assertThat(num(indicador(param, "total_salidas"))).isEqualTo(num(total.get("total_salidas")));
        assertThat(detalle(param)).allSatisfy(f -> {
            assertThat(f.get("numero_movimiento")).isNotNull();
            assertThat(f.get("producto")).isNotNull();
            assertThat(String.valueOf(f.get("fecha_hora")).substring(0, 10)).isGreaterThanOrEqualTo(desde.toString());
        });
    }

    @Test
    void cajaClasificaIngresosYEgresosIgualQueElArqueo() {
        Map<String, Object> estatico = reportes.movimientosCaja(owner, empresa, diaConDatos);
        Map<String, Object> resumen = reportes.resumenCajas(owner, empresa, diaConDatos);
        ReporteDtos.CajasParamRequest filtros = new ReporteDtos.CajasParamRequest();
        filtros.setFechaInicial(diaConDatos);
        filtros.setFechaFinal(diaConDatos);
        Map<String, Object> param = reportes.cajasParametrizado(owner, empresa, filtros);

        Map<String, Object> totalMovs = map(estatico.get("total_empresa"));
        assertThat(num(totalMovs.get("total_movimientos"))).isPositive();
        assertThat(num(totalResumenDe(resumen).get("total_cajas"))).isPositive();
        Map<String, Object> totalResumen = map(resumen.get("total_empresa"));
        assertThat(num(indicador(param, "total_ingresos"))).isEqualTo(num(totalMovs.get("total_ingresos")));
        assertThat(num(indicador(param, "total_egresos"))).isEqualTo(num(totalMovs.get("total_egresos")));
        assertThat(num(totalResumen.get("ingresos"))).isEqualTo(num(totalMovs.get("total_ingresos")));
        assertThat(num(totalResumen.get("flujo_neto")))
                .isEqualTo(Math.round((num(totalMovs.get("total_ingresos")) - num(totalMovs.get("total_egresos"))) * 100) / 100.0);
        assertThat(num(totalResumen.get("cajas_abiertas")) + num(totalResumen.get("cajas_cerradas")))
                .isEqualTo(num(totalResumen.get("total_cajas")));
        assertThat(detalle(param)).hasSize((int) num(indicador(param, "total_movimientos")));
    }

    @Test
    void aperturaSumaYCierreNoCuentaEnLosIngresosDeCaja() {
        ReporteDtos.CajasParamRequest filtros = new ReporteDtos.CajasParamRequest();
        filtros.setFechaInicial(hoy.minusDays(40));
        filtros.setFechaFinal(hoy);
        Map<String, Object> param = reportes.cajasParametrizado(owner, empresa, filtros);
        ReporteDtos.VentasParamRequest ventas = new ReporteDtos.VentasParamRequest();
        ventas.setFechaInicial(hoy.minusDays(40));
        ventas.setFechaFinal(hoy);
        double totalVentas = num(indicador(reportes.ventasParametrizado(owner, empresa, ventas), "monto_total"));

        // Demo: cada sesion abre con 200 (APERTURA), registra sus ventas (INGRESO) y cierra (CIERRE, no cuenta).
        double aperturas = num(indicador(param, "monto_aperturas"));
        assertThat(aperturas).isEqualTo(200.0 * num(indicador(param, "total_sesiones")));
        assertThat(num(indicador(param, "total_egresos"))).isZero();
        assertThat(num(indicador(param, "total_ingresos"))).isEqualTo(Math.round((aperturas + totalVentas) * 100) / 100.0);
    }

    private static Map<String, Object> totalResumenDe(Map<String, Object> resumen) {
        return map(resumen.get("total_empresa"));
    }

    @Test
    void productosSeAgrupanYCoincidenConElTotal() {
        ReporteDtos.RespuestaReporte top = reportes.ejecutar(owner, empresa, "top 5 productos mas vendidos");
        assertThat(top.getFilas()).isNotEmpty().hasSizeLessThanOrEqualTo(10);
        ReporteDtos.RespuestaReporte porProducto = reportes.ejecutar(owner, empresa, "ventas por producto");
        assertThat(porProducto.getFilas()).allSatisfy(f -> assertThat(f).containsKeys("categoria_producto", "precio_promedio"));
        ReporteDtos.RespuestaReporte comparar = reportes.ejecutar(owner, empresa, "comparar periodos");
        assertThat(comparar.getFilas()).hasSize(2);
        ReporteDtos.RespuestaReporte caja = reportes.ejecutar(owner, empresa, "movimientos de caja de hoy");
        assertThat(caja.getEspecificacion().getFiltros()).containsEntry("periodo", "hoy");
        assertThat(caja.getColumnas()).extracting(ReporteDtos.Columna::getNombre).contains("monto");
        ReporteDtos.RespuestaReporte kardex = reportes.ejecutar(owner, empresa, "kardex");
        assertThat(kardex.getColumnas()).extracting(ReporteDtos.Columna::getNombre).contains("cantidad");
        assertThat(reportes.abastecimiento(owner, firstBranch())).isNotEmpty();
    }

    @Test
    void rechazaRangosInvertidos() {
        ReporteDtos.VentasParamRequest filtros = new ReporteDtos.VentasParamRequest();
        filtros.setFechaInicial(hoy);
        filtros.setFechaFinal(hoy.minusDays(1));
        assertThatThrownBy(() -> reportes.ventasParametrizado(owner, empresa, filtros)).isInstanceOf(ApiException.class);
    }

    private Integer firstBranch() {
        return jdbc.queryForObject("select min(id_sucursal) from sucursal where id_empresa = ?", Integer.class, empresa);
    }

    private static Object indicador(Map<String, Object> respuesta, String clave) {
        List<Map<String, Object>> indicadores = list(respuesta.get("resumen_gerencial"));
        return indicadores.stream().filter(i -> clave.equals(i.get("clave"))).findFirst()
                .orElseThrow(() -> new AssertionError("Falta el indicador " + clave)).get("valor");
    }

    private static List<Map<String, Object>> detalle(Map<String, Object> respuesta) {
        return list(respuesta.get("detalle_analitico"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static double num(Object value) {
        return ((Number) value).doubleValue();
    }

    private String createOwner() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "reportes-" + suffix + "@example.invalid";
        transactions.executeWithoutResult(status -> {
            Persona person = new Persona();
            person.setNombreCompleto("Cuenta de reportes");
            person.setDocumento("REP-" + suffix);
            person.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            person.setGenero("N/E");
            person.setTelefono("70000000");
            em.persist(person);
            Usuario user = new Usuario();
            user.setPersona(person);
            user.setEmail(email);
            user.setContrasena(passwords.hash("test-password"));
            user.setFechaCreacion(LocalDate.now());
            user.setActivo(true);
            em.persist(user);
        });
        return email;
    }
}
