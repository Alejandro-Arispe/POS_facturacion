package com.pos.usuarios;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.DefaultApplicationArguments;
import com.pos.common.seed.CatalogSeedRunner;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:catalog-upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","app.superadmin.email=","app.superadmin.password=","app.ai.gemini.api-key="})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class CatalogUpgradeRbacTests {
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {com.pos.support.RbacTestDatabase.configure(registry,"catalog",false);}
 @Autowired EntityManager em;
 @Autowired CatalogSeedRunner seeder;
 @Autowired PermisoRepository permissions;
 @Autowired RolPermisoRepository grants;
 @Autowired RolRepository roles;
 @MockitoBean com.pos.common.mail.EmailService mail;
 @MockitoBean com.pos.auditoria.CajaNegraWriter audit;
 static final String CODE="inventory_movement:read";
 // The migration ledger is a native table, outside Hibernate's create-drop lifecycle.
 @org.junit.jupiter.api.BeforeEach void prepareCompleteFixture(){em.createNativeQuery("DELETE FROM rbac_migration").executeUpdate();boot();}
 void boot(){seeder.run(new DefaultApplicationArguments(new String[0]));em.flush();em.clear();}
 Permiso canonicalPhysicalCode(){Permiso p=permissions.findByCodigoCanonico(CODE).orElseThrow();p.setCodigo(CODE);em.flush();return p;}
 Rol role(String name){Rol r=new Rol();r.setNombre(name);r.setDescripcion(name);r.setTipo("SISTEMA");r.setProtegido(true);em.persist(r);return r;}
 RolPermiso grant(Rol r,Permiso p,boolean active,boolean delegate){RolPermiso g=new RolPermiso();g.setRol(r);g.setPermiso(p);g.setActivo(active);g.setPuedeDelegar(delegate);em.persist(g);return g;}
 Permiso alias(Permiso canonical,boolean active){Permiso p=new Permiso();p.setCodigo("MOVIMIENTO_VER");p.setNombre("Movimiento histórico");p.setModulo(canonical.getModulo());p.setActivo(active);em.persist(p);return p;}
 @Test void existingCanonicalCodeIsReusedBeforeV1WithoutRecreatingAliasOrRevokedGrant(){
  Permiso p=canonicalPhysicalCode();Integer id=p.getIdPermiso();long count=permissions.count();
  var revoked=grants.findByRol_IdRol(roles.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow().getIdRol()).stream().filter(g->g.getPermiso().getIdPermiso().equals(id)).findFirst().orElseThrow();revoked.setActivo(false);Integer grantId=revoked.getIdRolPermiso();
  em.createNativeQuery("DELETE FROM rbac_migration WHERE version=1").executeUpdate();boot();boot();
  assertThat(permissions.count()).isEqualTo(count);assertThat(permissions.findByCodigoCanonico(CODE).orElseThrow().getIdPermiso()).isEqualTo(id);
  assertThat(grants.findById(grantId).orElseThrow().getActivo()).isFalse();assertThat(permissions.existsByCodigo("MOVIMIENTO_VER")).isFalse();
 }
 @Test void physicalCanonicalCodeWithoutMetadataIsReusedAndNormalized(){
  Permiso p=canonicalPhysicalCode();Integer id=p.getIdPermiso();p.setCodigoCanonico(null);p.setRecurso(null);p.setAccion(null);em.flush();long count=permissions.count();
  em.createNativeQuery("DELETE FROM rbac_migration").executeUpdate();boot();
  var migrated=permissions.findByCodigoCanonico(CODE).orElseThrow();assertThat(migrated.getIdPermiso()).isEqualTo(id);assertThat(migrated.getRecurso()).isEqualTo("inventory_movement");assertThat(permissions.count()).isEqualTo(count);
 }
 @Test void mixedAliasesAreConsolidatedAndRevokedDuplicatesWinWithoutLosingAssignments(){
  Permiso canonical=canonicalPhysicalCode(),legacy=alias(canonical,true);Integer keeper=canonical.getIdPermiso(),oldId=legacy.getIdPermiso();
  Rol both=role("Duplicado"),onlyAlias=role("Histórico");
  var canonicalGrant=grant(both,canonical,true,true);grant(both,legacy,false,false);var aliasGrant=grant(onlyAlias,legacy,true,false);
  em.flush();Integer chosenGrant=canonicalGrant.getIdRolPermiso(),oldGrant=aliasGrant.getIdRolPermiso();long count=permissions.count();boot();boot();
  assertThat(permissions.count()).isEqualTo(count-1);assertThat(permissions.findById(oldId)).isEmpty();assertThat(permissions.findByCodigoCanonico(CODE).orElseThrow().getIdPermiso()).isEqualTo(keeper);
  var preserved=grants.findById(chosenGrant).orElseThrow();assertThat(preserved.getActivo()).isFalse();assertThat(preserved.getPuedeDelegar()).isFalse();assertThat(grants.findByRol_IdRol(both.getIdRol())).hasSize(1);
  var converted=grants.findById(oldGrant).orElseThrow();assertThat(converted.getPermiso().getIdPermiso()).isEqualTo(keeper);assertThat(converted.getActivo()).isTrue();assertThat(converted.getPuedeDelegar()).isFalse();
 }
 @Test void inactiveAliasDoesNotReactivateCanonicalPermissionDuringRepair(){
  Permiso canonical=canonicalPhysicalCode();Integer id=canonical.getIdPermiso();alias(canonical,false);em.flush();boot();boot();
  assertThat(permissions.findById(id).orElseThrow().getActivo()).isFalse();
 }
}
