package com.pos.usuarios;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import com.pos.common.access.*;
import com.pos.common.exception.ApiException;
import com.pos.common.mail.EmailService;
import com.pos.common.security.JwtService;
import com.pos.empresas.domain.*;
import com.pos.empresas.dto.EditarPersonalCreate;
import com.pos.empresas.service.*;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.dto.*;
import com.pos.usuarios.repo.*;
import com.pos.usuarios.service.RolService;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:rbac-final;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","app.superadmin.email=","app.superadmin.password=","app.ai.gemini.api-key="
})
@AutoConfigureMockMvc
class RbacFinalValidationTests {
 @Autowired EntityManager em;
 @Autowired PlatformTransactionManager tm;
 @Autowired RolService roles;
 @Autowired RbacResolver resolver;
 @Autowired EmpresaAccess access;
 @Autowired RoleAdministrationPolicy policy;
 @Autowired RolRepository roleRepo;
 @Autowired PermisoRepository permissions;
 @Autowired RolPermisoRepository grants;
 @Autowired UsuarioRolRepository assignments;
 @Autowired SucursalService branches;
 @Autowired EmpresaService companies;
 @Autowired RbacMigration migration;
 @Autowired com.pos.common.seed.CatalogSeedRunner seeder;
 @Autowired com.pos.respaldos.RespaldoService backups;
 @Autowired JwtService jwt;
 @Autowired MockMvc mvc;
 @Autowired jakarta.persistence.EntityManagerFactory emf;
 @MockitoBean EmailService mail;
 @MockitoBean com.pos.auditoria.CajaNegraWriter fileAudit;
 Usuario owner,staff,outsider;
 Empresa company,other;
 Sucursal branch1,branch2;
 Rol protectedAdmin;
 TransactionTemplate tx;
 @BeforeEach void setup(){
  tx=new TransactionTemplate(tm);
  tx.executeWithoutResult(t->{
   owner=user();staff=user();outsider=user();company=company();other=company();branch1=branch(company);branch2=branch(company);
   protectedAdmin=roleRepo.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow();
   assign(owner,protectedAdmin,company.getIdEmpresa(),branch1.getIdSucursal());
   assign(outsider,protectedAdmin,other.getIdEmpresa(),null);
  });authenticate(owner);
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  com.pos.support.RbacTestDatabase.configure(registry,"final",true);
 }

 @Test void sameOpenSessionReflectsGrantAndRevocationOfFieldsRoleAssignmentUserAndPlan() throws Exception {
  Rol reader=custom("Lector vivo",List.of("product:read"),List.of());
  tx.executeWithoutResult(t->assign(staff,reader,company.getIdEmpresa(),null));
  String bearer=bearer(staff), url="/api/empresas/"+company.getIdEmpresa()+"/mis-permisos";
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product:read:costo"))));
  authenticate(owner);roles.editar(reader.getIdRol(),new RolUpdateRequest(null,List.of(id("product:read"),id("product:read:costo"))));
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("product:read:costo")));
  authenticate(owner);roles.editar(reader.getIdRol(),new RolUpdateRequest(null,List.of(id("product:read"))));
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("product:read:costo"))));
  authenticate(owner);roles.editar(reader.getIdRol(),new RolUpdateRequest(false,null));
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isNotFound());
  authenticate(owner);roles.editar(reader.getIdRol(),new RolUpdateRequest(true,null));
  tx.executeWithoutResult(t->assignments.findByUsuario_IdUsuarioAndIdEmpresa(staff.getIdUsuario(),company.getIdEmpresa()).forEach(a->a.setActivo(false)));
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isNotFound());
  tx.executeWithoutResult(t->{assignments.findByUsuario_IdUsuarioAndIdEmpresa(staff.getIdUsuario(),company.getIdEmpresa()).forEach(a->a.setActivo(true));em.find(Usuario.class,staff.getIdUsuario()).setActivo(false);});
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isUnauthorized());
  tx.executeWithoutResult(t->{em.find(Usuario.class,staff.getIdUsuario()).setActivo(true);em.find(Empresa.class,company.getIdEmpresa()).setActivo(false);});
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isForbidden());
 }
 @Test void expiredOrCancelledPlanBlocksSameJwtWithoutChangingRoleGrants() throws Exception {
  String bearer=bearer(owner),url="/api/roles/empresa/"+company.getIdEmpresa();
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isOk());
  tx.executeWithoutResult(t->em.createQuery("update HistorialSuscripcion h set h.fechaFin=:end where h.empresa.idEmpresa=:c").setParameter("end",LocalDate.now().minusDays(1)).setParameter("c",company.getIdEmpresa()).executeUpdate());
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isForbidden());
  tx.executeWithoutResult(t->em.createQuery("update HistorialSuscripcion h set h.fechaFin=:end,h.estado='cancelado' where h.empresa.idEmpresa=:c").setParameter("end",LocalDate.now().plusDays(30)).setParameter("c",company.getIdEmpresa()).executeUpdate());
  mvc.perform(get(url).header("Authorization",bearer)).andExpect(status().isForbidden());
 }
 @Test void platformIdentityHasItsOwnScopeAndCannotImpersonateBusinessOwner() throws Exception {
  tx.executeWithoutResult(t->em.find(Usuario.class,outsider.getIdUsuario()).setSuperAdmin(true));
  mvc.perform(get("/api/superadmin/resumen").header("Authorization",bearer(outsider))).andExpect(status().isOk());
  mvc.perform(get("/api/superadmin/resumen").header("Authorization",bearer(owner))).andExpect(status().isForbidden());
  mvc.perform(get("/api/roles/empresa/"+company.getIdEmpresa()).header("Authorization",bearer(outsider))).andExpect(status().isNotFound());
 }
 @Test void noPermissionRoleCannotUseProtectedReadsEvenWithMisleadingJwtLabels() throws Exception {
  Rol none=custom("Sin permisos",List.of(),List.of());tx.executeWithoutResult(t->assign(staff,none,company.getIdEmpresa(),branch1.getIdSucursal()));
  mvc.perform(get("/api/roles/empresa/"+company.getIdEmpresa()).header("Authorization",bearer(staff))).andExpect(status().isForbidden());
  mvc.perform(get("/api/roles/empresa/"+other.getIdEmpresa()).header("Authorization",bearer(staff))).andExpect(status().isNotFound());
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),branch1.getIdSucursal()).permissions()).isEmpty();
 }
 @Test void retiredFieldAdaptersCannotChangeCanonicalGrantsAndLegacyAliasesDoNotAuthorize() throws Exception {
  Rol reader=custom("Canonico",List.of("product:read"),List.of());
  assertThat(resolver.resolve(owner,company.getIdEmpresa(),null).allows("PRODUCTO_VER")).isFalse();
  for(String path:List.of("/api/roles/empresa/"+company.getIdEmpresa()+"/mis-campos","/api/roles/"+reader.getIdRol()+"/campos"))
   mvc.perform(get(path).header("Authorization",bearer(owner))).andExpect(status().isGone());
  mvc.perform(put("/api/roles/"+reader.getIdRol()+"/campos").header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"formulario\":\"producto\",\"campos\":[]}")).andExpect(status().isGone());
  mvc.perform(put("/api/roles/"+reader.getIdRol()).header("Authorization",bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"campos\":[]}")).andExpect(status().isBadRequest());
  assertThat(grants.findConcesiones(List.of(reader.getIdRol()))).extracting(g->g.getPermiso().getCodigoCanonico()).containsExactly("product:read");
 }
 @Test void concurrentMetadataEditsPreserveCommittedChangesAfterWaitingForCompanyLock() throws Exception {
  Rol target=custom("Antes simultaneo",List.of("product:read"),List.of());
  var firstLocked=new java.util.concurrent.CountDownLatch(1);var secondLoaded=new java.util.concurrent.CountDownLatch(1);
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var first=pool.submit(()->{authenticate(owner);try {tx.executeWithoutResult(t->{policy.lock(company.getIdEmpresa());firstLocked.countDown();await(secondLoaded);em.find(Rol.class,target.getIdRol()).setNombre("Nombre confirmado");em.flush();});}finally{SecurityContextHolder.clearContext();}});
   var second=pool.submit(()->{await(firstLocked);authenticate(owner);try {tx.executeWithoutResult(t->{em.find(Rol.class,target.getIdRol());secondLoaded.countDown();roles.editar(target.getIdRol(),new RolUpdateRequest(null,"Descripción simultánea",null,null,null,null));});}finally{SecurityContextHolder.clearContext();}});
   first.get(20,java.util.concurrent.TimeUnit.SECONDS);second.get(20,java.util.concurrent.TimeUnit.SECONDS);
   var stored=roleRepo.findById(target.getIdRol()).orElseThrow();assertThat(stored.getNombre()).isEqualTo("Nombre confirmado");assertThat(stored.getDescripcion()).isEqualTo("Descripción simultánea");
  } finally {pool.shutdownNow();}
 }

 @Test void concurrentDelegationRevocationIsRecheckedAfterWaitingForCompanyLock() throws Exception {
  List<String> codes=List.of("role:read","role:create","role:create:nombre","role:create:permissions","product:read");
  Rol manager=custom("Gestor revocable",codes,List.of("product:read"));tx.executeWithoutResult(t->assign(staff,manager,company.getIdEmpresa(),null));
  var locked=new java.util.concurrent.CountDownLatch(1);var read=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var revoker=pool.submit(()->{authenticate(owner);try {tx.executeWithoutResult(t->{policy.lock(company.getIdEmpresa());locked.countDown();await(read);roles.editar(manager.getIdRol(),new RolUpdateRequest(null,null,null,null,List.of(),null));});}finally{SecurityContextHolder.clearContext();}});
   var editor=pool.submit(()->{await(locked);authenticate(staff);try {return tx.execute(t->{resolver.resolve(staff,company.getIdEmpresa(),null);read.countDown();roles.crear(company.getIdEmpresa(),new RolCreateRequest("No debe crearse",List.of(id("product:read"))));return 201;});}catch(ApiException ex){return ex.getStatus().value();}finally{SecurityContextHolder.clearContext();}});
   revoker.get(20,java.util.concurrent.TimeUnit.SECONDS);assertThat(editor.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(403);
   assertThat(roleRepo.findByNombreIgnoreCaseAndIdEmpresa("No debe crearse",company.getIdEmpresa())).isEmpty();
  } finally {pool.shutdownNow();}
 }
 @Test void simultaneousAttemptsToRemoveOwnerBothFailAndLeaveAnAdministrator() throws Exception {
  Rol harmless=custom("Personal",List.of("product:read"),List.of());
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  try {
   var task=(java.util.concurrent.Callable<Integer>)()->{await(start);authenticate(owner);try {branches.editarPersonal(company.getIdEmpresa(),new EditarPersonalCreate(owner.getEmail(),List.of(branch1.getIdSucursal()),harmless.getIdRol(),false));return 200;}catch(ApiException ex){return ex.getStatus().value();}finally{SecurityContextHolder.clearContext();}};
   var first=pool.submit(task);var second=pool.submit(task);start.countDown();
   assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(403);assertThat(second.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(403);
   assertThat(assignments.findAutorizaciones(owner.getIdUsuario(),company.getIdEmpresa())).anyMatch(RbacResolver::companyAdministrator);
  } finally {pool.shutdownNow();}
 }
 @Test void requestScopedResolutionUsesTwoQueriesIndependentOfFieldAndRowCountAndRevokesOnNextRequest() {
  Rol reader=custom("Carga",List.of("product:read","product:read:nombre","product:read:costo"),List.of());tx.executeWithoutResult(t->assign(staff,reader,company.getIdEmpresa(),null));
  var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();stats.setStatisticsEnabled(true);stats.clear();
  var attrs=new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest("GET","/api/productos"));
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attrs);
  try {for(int row=0;row<100;row++) {var g=resolver.resolve(staff,company.getIdEmpresa(),null);for(String field:ProductFields.ALL) resolver.field(g,"product","read",field);}assertThat(stats.getPrepareStatementCount()).isEqualTo(2);}
  finally {org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();attrs.requestCompleted();stats.setStatisticsEnabled(false);}
  authenticate(owner);roles.editar(reader.getIdRol(),new RolUpdateRequest(null,List.of("product:read").stream().map(this::id).toList()));
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),null).allows("product:read:costo")).isFalse();
 }
 @Test void branchManagerCannotBorrowCompanyAssignmentToDelegateBranchPowersGlobally() {
  Rol companyReader=custom("Presencia global",List.of("product:read"),List.of());
  List<String> codes=List.of("role:read","role:create","role:create:nombre","role:create:permissions","product:read");Rol branchManager=custom("Gestor local",codes,codes);
  tx.executeWithoutResult(t->{assign(staff,companyReader,company.getIdEmpresa(),null);assign(staff,branchManager,company.getIdEmpresa(),branch1.getIdSucursal());});authenticate(staff);
  assertStatus(403,()->roles.crear(company.getIdEmpresa(),new RolCreateRequest("Escalado global",List.of(id("product:read")))));
 }
 static void await(java.util.concurrent.CountDownLatch latch){try{if(!latch.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Timed out synchronizing editors");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
 String bearer(Usuario user){return "Bearer "+jwt.createAccessToken(user.getIdUsuario(),user.getEmail(),List.of("ADMINISTRADOR"));}
 private Rol custom(String name,List<String> codes,List<String> delegate){return roleRepo.findById(roles.crear(company.getIdEmpresa(),new RolCreateRequest(name,"Prueba",true,ids(codes),ids(delegate),null)).rol().idRol()).orElseThrow();}
 private int id(String code){return permissions.findByCodigoCanonico(code).orElseThrow().getIdPermiso();}
 private List<Integer> ids(List<String> c){return c.stream().map(this::id).toList();}
 private void authenticate(Usuario u){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,List.of()));}
 private void assertStatus(int status,org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(status));}
 private Usuario user(){
  String unique=UUID.randomUUID().toString().substring(0,18);Persona p=new Persona();p.setNombreCompleto("RBAC");p.setDocumento(unique);p.setFechaNacimiento(LocalDate.of(2000,1,1));p.setGenero("Otro");p.setTelefono("70000000");em.persist(p);
  Usuario u=new Usuario();u.setPersona(p);u.setEmail(unique+"@rbac.test");u.setContrasena("fixture");u.setFechaCreacion(LocalDate.now());u.setActivo(true);em.persist(u);return u;
 }
 private Empresa company(){String unique=UUID.randomUUID().toString();Empresa c=new Empresa();c.setNombre("RBAC");c.setRazonSocial("RBAC");c.setNit(unique.substring(0,20));c.setCorreo(unique+"@rbac.test");c.setFechaCreacion(LocalDate.now());em.persist(c);HistorialSuscripcion h=new HistorialSuscripcion();h.setEmpresa(c);h.setPlan(em.createQuery("select p from Plan p where p.nombre='Pro'",Plan.class).getSingleResult());h.setEstado("activo");h.setFechaInicio(LocalDate.now());h.setFechaFin(LocalDate.now().plusDays(30));em.persist(h);return c;}
 private Sucursal branch(Empresa c){Sucursal b=new Sucursal();b.setEmpresa(c);b.setNombre("Sucursal");b.setDireccion("Calle 1");b.setCiudad("La Paz");b.setTelefono("70000000");b.setFechaRegistro(LocalDate.now());em.persist(b);return b;}
 private void assign(Usuario u,Rol r,Integer c,Integer b){UsuarioRol a=new UsuarioRol();a.setUsuario(u);a.setRol(r);a.setIdEmpresa(c);a.setIdSucursal(b);a.setFecha(LocalDate.now());em.persist(a);}
}
