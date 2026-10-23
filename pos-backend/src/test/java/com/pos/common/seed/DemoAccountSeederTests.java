package com.pos.common.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.pos.common.security.PasswordService;
import com.pos.usuarios.domain.Persona;
import com.pos.usuarios.domain.Usuario;

import jakarta.persistence.EntityManager;

@ActiveProfiles("seed-demo")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-seeder;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.config.import=",
        "app.superadmin.email=", "app.superadmin.password=", "app.ai.gemini.api-key="
})
class DemoAccountSeederTests {

    @Autowired DemoAccountSeeder seeder;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired PasswordService passwords;

    @Test
    void createsBalancedDatasetAndPreservesEditsOnRepeatedExecution() {
        String email = createOwner(true);
        String originalHash = jdbc.queryForObject("select contrasena from usuario where email = ?", String.class, email);
        var result = seeder.seed(email.toUpperCase());
        Integer id = result.companyId();
        assertThat(result.created()).isTrue();
        assertThat(count("select count(*) from sucursal where id_empresa = ?", id)).isEqualTo(2);
        assertThat(count("select count(*) from producto where id_empresa = ?", id)).isEqualTo(12);
        assertThat(count("select count(*) from categoria_cliente where id_empresa = ?", id)).isEqualTo(2);
        assertThat(count("select count(*) from cliente c join categoria_cliente cc on cc.id_categoria_cliente = c.id_categoria_cliente where cc.id_empresa = ?", id)).isEqualTo(8);
        assertThat(count("select count(*) from venta v join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion join caja ca on ca.id_caja = cs.id_caja join sucursal s on s.id_sucursal = ca.id_sucursal where s.id_empresa = ?", id)).isEqualTo(120);
        assertThat(count("select count(*) from stock st join producto p on p.id_producto = st.id_producto where p.id_empresa = ?", id)).isEqualTo(24);

        // Kardex y stock coinciden por producto y sucursal, incluidos los productos agotados.
        assertThat(count("""
                select count(*) from stock st join producto p on p.id_producto = st.id_producto
                where p.id_empresa = ? and st.cantidad <> (
                    select coalesce(sum(case when tm.direccion = 'ENTRADA' then mi.cantidad else -mi.cantidad end), 0)
                    from movimiento_inventario mi join tipo_movimiento tm on tm.id_tipo_movimiento = mi.id_tipo_movimiento
                    where mi.id_producto = st.id_producto and mi.id_sucursal = st.id_sucursal)
                """, id)).isZero();
        assertThat(count("""
                select count(*) from venta v join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion
                join caja ca on ca.id_caja = cs.id_caja join sucursal s on s.id_sucursal = ca.id_sucursal
                where s.id_empresa = ? and (v.total <> (select sum(vp.monto) from venta_pago vp where vp.id_venta = v.id_venta)
                    or v.total <> (select sum(d.total) from detalle_venta d where d.id_venta = v.id_venta)
                    or v.total <> v.subtotal - v.descuento_total)
                """, id)).isZero();
        assertThat(count("""
                select count(*) from caja_sesion cs join caja ca on ca.id_caja = cs.id_caja
                join sucursal s on s.id_sucursal = ca.id_sucursal where s.id_empresa = ?
                and cs.monto_final <> cs.monto_inicial + (select sum(v.total) from venta v where v.id_caja_sesion = cs.id_caja_sesion)
                """, id)).isZero();

        jdbc.update("update producto set precio = 99.00 where id_empresa = ?", id);
        long users = count("select count(*) from usuario");
        long sales = count("select count(*) from venta");
        var repeated = seeder.seed(email);
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.companyId()).isEqualTo(id);
        assertThat(count("select count(*) from usuario")).isEqualTo(users);
        assertThat(count("select count(*) from venta")).isEqualTo(sales);
        assertThat(count("select count(*) from producto where id_empresa = ? and precio = 99.00", id)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select contrasena from usuario where email = ?", String.class, email)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("select super_admin from usuario where email = ?", Boolean.class, email)).isFalse();
    }

    @Test
    void rejectsMissingOrInactiveAccountsWithoutCreatingCompanies() {
        String inactive = createOwner(false);
        long before = count("select count(*) from empresa");
        assertThatThrownBy(() -> seeder.seed("missing@example.invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> seeder.seed(inactive)).isInstanceOf(IllegalStateException.class);
        assertThat(count("select count(*) from empresa")).isEqualTo(before);
    }

    @Test
    void rollsBackAllInsertedDataIfACatalogIsMissing() {
        String email = createOwner(true);
        long companies = count("select count(*) from empresa");
        long users = count("select count(*) from usuario");
        long products = count("select count(*) from producto");
        jdbc.update("update metodo_pago set nombre = 'QR-TEST-MISSING' where nombre = 'QR'");
        try {
            assertThatThrownBy(() -> seeder.seed(email)).isInstanceOf(IllegalStateException.class).hasMessageContaining("QR");
            assertThat(count("select count(*) from empresa")).isEqualTo(companies);
            assertThat(count("select count(*) from usuario")).isEqualTo(users);
            assertThat(count("select count(*) from producto")).isEqualTo(products);
        } finally {
            jdbc.update("update metodo_pago set nombre = 'QR' where nombre = 'QR-TEST-MISSING'");
        }
    }

    private String createOwner(boolean active) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "owner-" + suffix + "@example.invalid";
        transactions.executeWithoutResult(status -> {
            Persona person = new Persona();
            person.setNombreCompleto("Cuenta de prueba");
            person.setDocumento("TEST-" + suffix);
            person.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            person.setGenero("N/E");
            person.setTelefono("70000000");
            em.persist(person);
            Usuario user = new Usuario();
            user.setPersona(person);
            user.setEmail(email);
            user.setContrasena(passwords.hash("test-password"));
            user.setFechaCreacion(LocalDate.now());
            user.setActivo(active);
            em.persist(user);
        });
        return email;
    }

    private long count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Long.class, parameters);
    }
}
