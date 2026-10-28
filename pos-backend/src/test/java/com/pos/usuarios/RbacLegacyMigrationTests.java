package com.pos.usuarios;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import com.pos.common.access.RbacMigration;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;
import com.pos.empresas.domain.Empresa;
import jakarta.persistence.EntityManager;

@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:legacy-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","spring.jpa.show-sql=false"})
@Import(RbacMigration.class)
@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(replace=org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE)
class RbacLegacyMigrationTests {
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  com.pos.support.RbacTestDatabase.configure(registry,"migration",false);
 }

 @Autowired EntityManager em;
 @Autowired RbacMigration migration;
 @Autowired PermisoRepository permissions;
 @Autowired RolPermisoRepository grants;
 @Test void migratesLegacyIdsAndFieldsWithoutBroadeningOrRestoringGrants(){
  Modulo users=module("USUARIOS"),products=module("INVENTARIO");Modulo sales=module("VENTAS"),cash=module("CAJAS");
  Permiso read=permission("PRODUCTO_VER",products),write=permission("PRODUCTO_EDITAR",products),editRoles=permission("ROL_EDITAR",users);
  Permiso sell=permission("VENTA_CREAR",sales),discount=permission("VENTA_DESCUENTO",sales),cashEdit=permission("CAJA_EDITAR",cash);
  Empresa company=new Empresa();company.setNombre("Legado");company.setRazonSocial("Legado");company.setNit("RBAC-LEGACY");company.setCorreo("legacy@rbac.test");company.setFechaCreacion(LocalDate.now());em.persist(company);
  Rol owner=role("ADMINISTRADOR",null,"SISTEMA"),cashier=role("CAJERO",company.getIdEmpresa(),"CUSTOM");
  grant(owner,sell,true);grant(owner,discount,true);grant(owner,cashEdit,true);
  grant(cashier,sell,true);grant(cashier,discount,true);grant(cashier,cashEdit,true);
  grant(owner,read,true);grant(owner,write,true);grant(owner,editRoles,true);
  grant(cashier,read,true);grant(cashier,write,true);grant(cashier,write,false);
  field(cashier,"costo","OCULTO");field(cashier,"precio","SOLO_LECTURA");
  em.flush();Integer readId=read.getIdPermiso();migration.migrate();em.flush();em.clear();
  assertThat(permissions.findByCodigoCanonico("product:read").orElseThrow().getIdPermiso()).isEqualTo(readId);
  var effective=grants.findConcesiones(List.of(cashier.getIdRol()));
  assertThat(effective).extracting(x->x.getPermiso().getCodigoCanonico()).contains("product:read","product:read:precio").doesNotContain("product:read:costo","product:update","product:update:precio");
  assertThat(effective).allMatch(x->!Boolean.TRUE.equals(x.getPuedeDelegar()));
  var admin=grants.findConcesiones(List.of(owner.getIdRol()));
  assertThat(admin).extracting(x->x.getPermiso().getCodigoCanonico()).contains("role:update:permissions","role:assign");
  assertThat(admin).allMatch(x->Boolean.TRUE.equals(x.getPuedeDelegar()));
  assertThat(effective).extracting(x->x.getPermiso().getCodigoCanonico()).contains("sale:create:descuento","cash_movement:create").doesNotContain("sale:create:precio_unitario","sale:collect");
  assertThat(admin).extracting(x->x.getPermiso().getCodigoCanonico()).contains("sale:create:precio_unitario","sale:create:descuento","sale:collect","cash_movement:create");
  var revoked=effective.stream().filter(x->"sale:create:descuento".equals(x.getPermiso().getCodigoCanonico())).findFirst().orElseThrow();revoked.setActivo(false);em.flush();
  assertThat(permissions.findByCodigoCanonico("sale:collect").orElseThrow().getNombre()).isEqualTo("Registrar cobros de crédito");
  assertThat(permissions.findByCodigoCanonico("cash_movement:read").orElseThrow().getNombre()).isEqualTo("Ver movimientos de caja");
  long count=permissions.count();migration.migrate();assertThat(permissions.count()).isEqualTo(count);assertThat(grants.findConcesiones(List.of(cashier.getIdRol()))).noneMatch(g->"sale:create:descuento".equals(g.getPermiso().getCodigoCanonico()));
  em.unwrap(org.hibernate.Session.class).doWork(connection->{
    var script=new org.springframework.core.io.ClassPathResource("db/rbac/"+(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")?"postgresql":"h2")+"/V001__schema.sql");
    executeScript(connection,script);
    executeScript(connection,script);
    var v3=new org.springframework.core.io.ClassPathResource("db/rbac/"+(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")?"postgresql":"h2")+"/V003__client_scope.sql");
    executeScript(connection,v3);
    executeScript(connection,v3);
  });
 }
 private void executeScript(java.sql.Connection connection,org.springframework.core.io.Resource script) throws java.sql.SQLException {
  if(connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
   try(var statement=connection.createStatement();var input=script.getInputStream()) {
    // JDBC understands dollar-quoted DO blocks; Spring's generic semicolon splitter does not.
    statement.execute(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   } catch(java.io.IOException error) { throw new java.sql.SQLException("Cannot read migration fixture",error); }
  } else org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
 }
 private Modulo module(String code){Modulo m=new Modulo();m.setCodigo(code);m.setNombre(code);em.persist(m);return m;}
 private Permiso permission(String code,Modulo m){Permiso p=new Permiso();p.setCodigo(code);p.setNombre(code);p.setModulo(m);em.persist(p);return p;}
 private Rol role(String name,Integer company,String type){Rol r=new Rol();r.setNombre(name);r.setDescripcion(name);r.setIdEmpresa(company);r.setTipo(type);em.persist(r);return r;}
 private void grant(Rol r,Permiso p,boolean active){RolPermiso g=new RolPermiso();g.setRol(r);g.setPermiso(p);g.setActivo(active);em.persist(g);}
 private void field(Rol r,String name,String state){PrivilegioCampo p=new PrivilegioCampo();p.setIdRol(r.getIdRol());p.setFormulario("producto");p.setCampo(name);p.setEstado(state);em.persist(p);}
}
