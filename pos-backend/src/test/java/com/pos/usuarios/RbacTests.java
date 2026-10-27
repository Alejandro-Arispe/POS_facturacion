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
 "spring.datasource.url=jdbc:h2:mem:rbac;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","app.superadmin.email=","app.superadmin.password=","app.ai.gemini.api-key="
})
@AutoConfigureMockMvc
class RbacTests {
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  com.pos.support.RbacTestDatabase.configure(registry,"roles",false);
 }
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
 @Test void deniesUnknownCodesAndMissingMembership(){
  assertStatus(403,()->access.requirePermiso(owner,company.getIdEmpresa(),"ROL_EDTIAR"));
  assertStatus(403,()->access.requirePermiso(owner,company.getIdEmpresa(),"unknown:read"));
  assertStatus(404,()->roles.listarPorEmpresa(other.getIdEmpresa()));
  assertStatus(401,()->resolver.resolve(null,company.getIdEmpresa(),null));
 }
 @Test void unionsAllActiveRolesWithinTheirBranchAndRevokesImmediately(){
  Rol a=custom("LECTOR",List.of("product:read"),List.of());
  Rol b=custom("VENTAS",List.of("sale:read"),List.of());
  tx.executeWithoutResult(t->{assign(staff,a,company.getIdEmpresa(),branch1.getIdSucursal());assign(staff,b,company.getIdEmpresa(),branch2.getIdSucursal());});
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),null).permissions()).containsKeys("product:read","sale:read");
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),branch1.getIdSucursal()).permissions()).containsKey("product:read").doesNotContainKey("sale:read");
  roles.editar(a.getIdRol(),new RolUpdateRequest(false,null));
  assertStatus(404,()->resolver.resolve(staff,company.getIdEmpresa(),branch1.getIdSucursal()));
  roles.editar(a.getIdRol(),new RolUpdateRequest(true,List.of()));
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),branch1.getIdSucursal()).permissions()).isEmpty();
 }
 @Test void filtersRevokedPermissionAndCrossTenantRoleEvenWithValidAssignment(){
  Rol a=custom("LECTOR",List.of("product:read"),List.of());
  tx.executeWithoutResult(t->assign(staff,a,company.getIdEmpresa(),null));
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),null).allows("product:read")).isTrue();
  RolPermiso row=grants.findByRol_IdRol(a.getIdRol()).get(0);row.setActivo(false);grants.save(row);
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),null).permissions()).isEmpty();
  tx.executeWithoutResult(t->{Rol foreign=new Rol();foreign.setNombre("Ajeno");foreign.setDescripcion("Ajeno");foreign.setTipo("CUSTOM");foreign.setIdEmpresa(other.getIdEmpresa());em.persist(foreign);assign(staff,foreign,company.getIdEmpresa(),branch2.getIdSucursal());});
  assertThat(resolver.resolve(staff,company.getIdEmpresa(),branch2.getIdSucursal()).permissions()).isEmpty();
 }
 @Test void inactiveCatalogPermissionRevokesWithoutJwtRefresh(){
  Rol a=custom("LECTOR",List.of("product:read"),List.of());tx.executeWithoutResult(t->assign(staff,a,company.getIdEmpresa(),null));
  Permiso p=permissions.findByCodigoCanonico("product:read").orElseThrow();
  try {p.setActivo(false);permissions.save(p);assertThat(resolver.resolve(staff,company.getIdEmpresa(),null).permissions()).isEmpty();}
  finally{p.setActivo(true);permissions.save(p);}
 }
 @Test void checksTenantAndPermissionForRoleDetailsAndWrites(){
  Rol a=custom("LECTOR",List.of("product:read"),List.of());
  authenticate(outsider);assertStatus(404,()->roles.obtenerPorId(a.getIdRol()));
  tx.executeWithoutResult(t->assign(staff,a,company.getIdEmpresa(),null));authenticate(staff);
  assertStatus(403,()->roles.obtenerPorId(a.getIdRol()));
  assertStatus(403,()->roles.editar(a.getIdRol(),new RolUpdateRequest(false,List.of())));
  assertStatus(403,()->roles.crear(company.getIdEmpresa(),new RolCreateRequest("hack",List.of())));
 }
 @Test void possessionDoesNotImplyDelegationAndCannotElevateOwnRole(){
  List<String> codes=List.of("role:read","role:read:permissions","role:update","role:update:permissions","role:create","role:create:nombre","role:create:permissions","product:read");
  Rol manager=custom("GESTOR",codes,List.of());tx.executeWithoutResult(t->assign(staff,manager,company.getIdEmpresa(),null));authenticate(staff);
  assertStatus(403,()->roles.crear(company.getIdEmpresa(),new RolCreateRequest("Hijo",List.of(id("product:read")))));
  assertStatus(403,()->roles.editar(manager.getIdRol(),new RolUpdateRequest(null,null,null,ids(codes),ids(codes),null)));
 }
 @Test void limitedManagerCanDelegateOnlyApprovedSubset(){
  List<String> codes=List.of("role:read","role:read:permissions","role:create","role:create:nombre","role:create:permissions","product:read");
  Rol manager=custom("GESTOR",codes,List.of("product:read"));tx.executeWithoutResult(t->assign(staff,manager,company.getIdEmpresa(),null));authenticate(staff);
  var child=roles.crear(company.getIdEmpresa(),new RolCreateRequest("Lector delegado",null,null,List.of(id("product:read")),List.of(),null));
  assertThat(child.permisoIds()).containsExactly(id("product:read"));
  assertStatus(403,()->roles.crear(company.getIdEmpresa(),new RolCreateRequest("Elevado",List.of(id("sale:create")))));
 }
 @Test void updatesMetadataAndFieldsAtomicallyAndPreservesOnFailure(){
  Rol a=custom("ANTES",List.of("product:read"),List.of());
  var updated=roles.editar(a.getIdRol(),new RolUpdateRequest("Después","Descripción",true,List.of(id("product:read"),id("product:read:costo")),List.of(),null));
  assertThat(updated.nombre()).isEqualTo("Después");assertThat(updated.descripcion()).isEqualTo("Descripción");
  assertThat(updated.rolPermisos()).anyMatch(p->p.idPermiso().equals(id("product:read:costo")) && p.activo());
  assertStatus(400,()->roles.editar(a.getIdRol(),new RolUpdateRequest("INCORRECTO","No guardar",false,List.of(999999),List.of(),null)));
  var stored=roleRepo.findById(a.getIdRol()).orElseThrow();assertThat(stored.getNombre()).isEqualTo("Después");assertThat(stored.getActivo()).isTrue();
 }
 @Test void roleNamesAreScopedAndProtectedIdentitiesCannotBeEdited(){
  custom("Nombre común",List.of(),List.of());
  assertStatus(409,()->custom(" nombre común ",List.of(),List.of()));
  assertStatus(400,()->custom("ADMINISTRADOR",List.of(),List.of()));
  assertStatus(403,()->roles.editar(protectedAdmin.getIdRol(),new RolUpdateRequest(false,List.of())));
  authenticate(outsider);assertThat(roles.crear(other.getIdEmpresa(),new RolCreateRequest("Nombre común",List.of())).rol().idEmpresa()).isEqualTo(other.getIdEmpresa());
 }
 @Test void protectsOwnerAndRejectsAssigningProtectedRoles(){
  assertStatus(403,()->branches.editarPersonal(company.getIdEmpresa(),new EditarPersonalCreate(owner.getEmail(),List.of(branch1.getIdSucursal()),protectedAdmin.getIdRol(),false)));
  assertStatus(403,()->policy.assign(owner,company.getIdEmpresa(),staff,protectedAdmin,List.of(branch1.getIdSucursal())));
  assertThat(assignments.findAutorizaciones(owner.getIdUsuario(),company.getIdEmpresa())).isNotEmpty();
 }
 @Test void branchManagerCannotExpandAssignmentScopeOrChangeCompanySharedRoles(){
  List<String> codes=List.of("role:read","role:read:permissions","role:update","role:update:nombre","role:assign","user:update","product:read");
  Rol manager=custom("GESTOR SUCURSAL",codes,codes);Rol reader=custom("LECTOR",List.of("product:read"),List.of());
  tx.executeWithoutResult(t->assign(staff,manager,company.getIdEmpresa(),branch1.getIdSucursal()));authenticate(staff);
  assertStatus(403,()->roles.editar(reader.getIdRol(),new RolUpdateRequest("Nombre nuevo",null,null,null,null,null)));
  assertStatus(404,()->policy.assign(staff,company.getIdEmpresa(),outsider,reader,List.of(branch2.getIdSucursal())));
 }
 @Test void invitationRevalidatesIssuerAndRejectsLegacyTokens(){
  Rol reader=custom("LECTOR",List.of("product:read"),List.of());
  String token=jwt.createInvitationToken(company.getIdEmpresa(),staff.getIdUsuario(),reader.getIdRol(),List.of(branch1.getIdSucursal()),1,owner.getIdUsuario());
  tx.executeWithoutResult(t->{var rows=assignments.findAutorizaciones(owner.getIdUsuario(),company.getIdEmpresa());rows.forEach(r->r.setActivo(false));assignments.saveAll(rows);});
  assertStatus(404,()->branches.aceptarInvitacionEmpleado(token));
  String legacy=jwt.createInvitationToken(company.getIdEmpresa(),staff.getIdUsuario(),reader.getIdRol(),List.of(branch1.getIdSucursal()),1,null);
  assertThatThrownBy(()->branches.aceptarInvitacionEmpleado(legacy)).isInstanceOf(ApiException.class);
 }
 @Test void invitationIsSingleUseAndRevokedWhenStaffAssignmentsChange(){
  Rol reader=custom("LECTOR",List.of("product:read"),List.of());
  var request=new com.pos.empresas.dto.InvitacionEmpleadoCreate(staff.getEmail(),List.of(branch1.getIdSucursal()),reader.getIdRol());
  String token=token(branches.invitarEmpleado(company.getIdEmpresa(),request).linkInvitacion());
  assertThat(branches.aceptarInvitacionEmpleado(token).mensaje()).contains("aceptada");
  assertStatus(400,()->branches.aceptarInvitacionEmpleado(token));
  String pending=token(branches.invitarEmpleado(company.getIdEmpresa(),request).linkInvitacion());
  branches.editarPersonal(company.getIdEmpresa(),new EditarPersonalCreate(staff.getEmail(),List.of(branch1.getIdSucursal()),reader.getIdRol(),false));
  assertStatus(400,()->branches.aceptarInvitacionEmpleado(pending));
  String renewed=token(branches.invitarEmpleado(company.getIdEmpresa(),request).linkInvitacion());
  assertThat(branches.aceptarInvitacionEmpleado(renewed).mensaje()).contains("aceptada");
  assertThat(assignments.findAutorizaciones(staff.getIdUsuario(),company.getIdEmpresa())).hasSize(1);
 }
 private String token(String link){return link.substring(link.lastIndexOf('/')+1);}
 @Test void deniesIndirectEscalationThroughBranchCreationAndBackups(){
  List<String> codes=List.of("branch:create","role:read","role:update","product:read");
  Rol manager=custom("GESTOR LOCAL",codes,codes);tx.executeWithoutResult(t->assign(staff,manager,company.getIdEmpresa(),branch1.getIdSucursal()));authenticate(staff);
  assertStatus(403,()->branches.crear(company.getIdEmpresa(),new com.pos.empresas.dto.SucursalCreate("Otra","Calle","70000000","La Paz")));
  assertStatus(403,()->backups.crear(company.getIdEmpresa(),staff));
  assertStatus(403,()->backups.restaurar(company.getIdEmpresa(),staff,"ninguno.json"));
 }
 @Test void roleFieldPermissionsAreIndependentAndCannotLeakThroughWriteResponse() throws Exception {
  Rol target=custom("OBJETIVO",List.of("product:read"),List.of());
  List<String> codes=List.of("role:read","role:update","role:update:nombre","product:read");
  Rol manager=custom("GESTOR NOMBRES",codes,List.of("product:read"));tx.executeWithoutResult(t->assign(staff,manager,company.getIdEmpresa(),null));authenticate(staff);
  var result=roles.editar(target.getIdRol(),new RolUpdateRequest("Nuevo nombre",null,null,null,null,null));
  assertThat(result.nombre()).isEqualTo("Nuevo nombre");assertThat(result.rolPermisos()).isNull();
  assertStatus(403,()->roles.editar(target.getIdRol(),new RolUpdateRequest(null,"Prohibido",null,null,null,null)));
  String token=jwt.createAccessToken(staff.getIdUsuario(),staff.getEmail(),List.of());
  mvc.perform(get("/api/roles/"+target.getIdRol()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.rol_permisos").doesNotExist());
 }
 @Test void migrationAndSeederDoNotRestoreRevokedGrants(){
  RolPermiso row=grants.findByRol_IdRol(protectedAdmin.getIdRol()).stream().filter(g->g.getPermiso().getIdPermiso().equals(id("product:read"))).findFirst().orElseThrow();
  long count=permissions.count();
  try {row.setActivo(false);grants.save(row);migration.migrate();seeder.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));assertThat(grants.findById(row.getIdRolPermiso()).orElseThrow().getActivo()).isFalse();assertThat(permissions.count()).isEqualTo(count);}
  finally{row.setActivo(true);grants.save(row);}
 }
 @Test void httpRejectsPayloadEscalationAndReturnsFreshEffectivePermissions() throws Exception {
  SecurityContextHolder.clearContext();
  mvc.perform(get("/api/roles/empresa/"+company.getIdEmpresa())).andExpect(status().isUnauthorized());
  authenticate(owner);
  Rol reader=custom("LECTOR",List.of("product:read"),List.of());tx.executeWithoutResult(t->assign(staff,reader,company.getIdEmpresa(),null));
  String token=jwt.createAccessToken(staff.getIdUsuario(),staff.getEmail(),List.of("ADMINISTRADOR"));
  mvc.perform(put("/api/roles/"+reader.getIdRol()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Escalado\",\"permiso_ids\":[]}")).andExpect(status().isForbidden());
  String ownerToken=jwt.createAccessToken(owner.getIdUsuario(),owner.getEmail(),List.of());
  mvc.perform(put("/api/roles/"+reader.getIdRol()).header("Authorization","Bearer "+ownerToken).contentType(MediaType.APPLICATION_JSON).content("{\"super_admin\":true}")).andExpect(status().isBadRequest());
  mvc.perform(put("/api/roles/"+reader.getIdRol()).header("Authorization","Bearer "+ownerToken).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":null}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/empresas/"+company.getIdEmpresa()+"/mis-permisos").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.permisos[0].codigo_canonico").value("product:read"));
  authenticate(owner);roles.editar(reader.getIdRol(),new RolUpdateRequest(false,null));
  mvc.perform(get("/api/empresas/"+company.getIdEmpresa()+"/mis-permisos").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
 }
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
