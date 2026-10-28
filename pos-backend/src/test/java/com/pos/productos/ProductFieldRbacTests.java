package com.pos.productos;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.persistence.EntityManager;
import com.pos.common.security.JwtService;
import com.pos.common.storage.ProductImageStorage;
import com.pos.empresas.domain.*;
import com.pos.productos.domain.*;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;
import com.pos.usuarios.service.RolService;
import com.pos.usuarios.dto.RolUpdateRequest;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:product-fields;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","app.superadmin.email=","app.superadmin.password=","app.ai.gemini.api-key="})
@AutoConfigureMockMvc
class ProductFieldRbacTests {
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  com.pos.support.RbacTestDatabase.configure(registry,"products",false);
 }
 @Autowired com.pos.productos.service.ProductoService products;
 @Autowired EntityManager em; @Autowired PlatformTransactionManager tm; @Autowired MockMvc mvc; @Autowired JwtService jwt;
 @Autowired RolRepository roles; @Autowired PermisoRepository permissions; @Autowired RolPermisoRepository grants; @Autowired RolService roleService;
 @MockitoBean ProductImageStorage images; @MockitoBean com.pos.common.mail.EmailService mail; @MockitoBean com.pos.auditoria.CajaNegraWriter audit;
 TransactionTemplate tx; Usuario owner,clerk,outsider; Empresa company,foreign; Rol role; SubcategoriaProducto sub,foreignSub; Producto product,foreignProduct;
 @BeforeEach void setup(){tx=new TransactionTemplate(tm);tx.executeWithoutResult(s->{owner=user();clerk=user();outsider=user();company=company();foreign=company();sub=sub(company);foreignSub=sub(foreign);role=new Rol();role.setNombre("Cajero");role.setDescripcion("Pruebas");role.setTipo("CUSTOM");role.setIdEmpresa(company.getIdEmpresa());em.persist(role);assign(owner,roles.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow(),company);assign(clerk,role,company);assign(outsider,roles.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow(),foreign);product=product(company,sub);foreignProduct=product(foreign,foreignSub);});}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 String auth(Usuario user){return "Bearer "+jwt.createAccessToken(user.getIdUsuario(),user.getEmail(),List.of("ADMINISTRADOR"));}
 String url(){return "/api/productos/"+product.getIdProducto();}
 void permit(String... codes){tx.executeWithoutResult(s->{grants.deleteAll(grants.findByRol_IdRol(role.getIdRol()));grants.flush();for(String code:codes){RolPermiso g=new RolPermiso();g.setRol(em.getReference(Rol.class,role.getIdRol()));g.setPermiso(permissions.findByCodigoCanonico(code).orElseThrow());g.setActivo(true);em.persist(g);}});}
 @Test void serializesOnlyReadableFieldsInDetailListAndRoot() throws Exception {
  permit("product:read","product:read:nombre","product:read:precio");
  mvc.perform(get(url()).header("Authorization",auth(clerk))).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Arroz")).andExpect(jsonPath("$.precio").value(20)).andExpect(jsonPath("$.costo").doesNotHaveJsonPath()).andExpect(jsonPath("$.subcategoria").doesNotHaveJsonPath()).andExpect(jsonPath("$.id_subcategoria").doesNotHaveJsonPath()).andExpect(jsonPath("$.imagen").doesNotHaveJsonPath());
  mvc.perform(get("/api/empresas/"+company.getIdEmpresa()+"/productos").header("Authorization",auth(clerk))).andExpect(status().isOk()).andExpect(jsonPath("$[0].costo").doesNotHaveJsonPath());
  mvc.perform(get("/api/productos").header("Authorization",auth(clerk))).andExpect(status().isOk()).andExpect(jsonPath("$[0].costo").doesNotHaveJsonPath()).andExpect(jsonPath("$[1]").doesNotHaveJsonPath());
 }
 @Test void deniesReadAndAllGeneralOperationsWithoutTheirPermission() throws Exception {
  permit("product:read:precio");
  mvc.perform(get(url()).header("Authorization",auth(clerk))).andExpect(status().isForbidden());
  mvc.perform(put(url()).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Cambio\"}")).andExpect(status().isForbidden());
  mvc.perform(delete(url()).header("Authorization",auth(clerk))).andExpect(status().isForbidden());
 }
 @Test void rejectsUnauthorizedPriceAndNullAtomically() throws Exception {
  permit("product:read","product:read:nombre","product:update","product:update:nombre","product:update:descripcion");
  for(String value:List.of("99","null")) mvc.perform(put(url()).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"No guardar\",\"precio\":"+value+"}")).andExpect(status().isForbidden());
  assertThat(em.find(Producto.class,product.getIdProducto()).getNombre()).isEqualTo("Arroz");
  mvc.perform(put(url()).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Arroz Premium\",\"descripcion\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Arroz Premium")).andExpect(jsonPath("$.precio").doesNotHaveJsonPath());
  Producto stored=em.find(Producto.class,product.getIdProducto());assertThat(stored.getDescripcion()).isNull();assertThat(stored.getPrecio()).isEqualByComparingTo("20");assertThat(stored.getCosto()).isEqualByComparingTo("12");
 }
 @Test void writeOnlyPriceDoesNotGrantReadAndPreservesOmittedCost() throws Exception {
  permit("product:update","product:update:precio");
  mvc.perform(put(url()).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content("{\"precio\":25.50}")).andExpect(status().isOk()).andExpect(jsonPath("$.precio").doesNotHaveJsonPath()).andExpect(jsonPath("$.costo").doesNotHaveJsonPath());
  assertThat(em.find(Producto.class,product.getIdProducto()).getPrecio()).isEqualByComparingTo("25.50");
  assertThat(em.find(Producto.class,product.getIdProducto()).getCosto()).isEqualByComparingTo("12");
 }
 @Test void clearNullableFieldIsDifferentFromOmittingIt() throws Exception {
  permit("product:read","product:read:costo","product:update","product:update:costo","product:update:nombre");
  mvc.perform(put(url()).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Nuevo\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.costo").value(12));
  mvc.perform(put(url()).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content("{\"costo\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.costo").hasJsonPath()).andExpect(jsonPath("$.costo").value(org.hamcrest.Matchers.nullValue()));
  assertThat(em.find(Producto.class,product.getIdProducto()).getCosto()).isNull();
 }
 @Test void creationRequiresCreateFieldsAndNeverBorrowsUpdatePermission() throws Exception {
  permit("product:create","product:create:nombre","product:create:id_subcategoria","product:create:unidad_medida","product:update:precio");
  String body="{\"nombre\":\"Nuevo\",\"id_subcategoria\":"+sub.getIdSubcategoria()+",\"unidad_medida\":\"Unidad\",\"precio\":30}";
  mvc.perform(post("/api/empresas/"+company.getIdEmpresa()+"/productos").header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  permit("product:create","product:create:nombre","product:create:id_subcategoria","product:create:unidad_medida","product:create:precio");
  mvc.perform(post("/api/empresas/"+company.getIdEmpresa()+"/productos").header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id_producto").isNumber()).andExpect(jsonPath("$.precio").doesNotHaveJsonPath()).andExpect(jsonPath("$.nombre").doesNotHaveJsonPath());
 }
 @Test void enforcesTenantOnEveryProductAndSubcategoryWrite() throws Exception {
  mvc.perform(get(url()).header("Authorization",auth(outsider))).andExpect(status().isNotFound());
  mvc.perform(get("/api/empresas/"+company.getIdEmpresa()+"/productos").header("Authorization",auth(outsider))).andExpect(status().isNotFound());
  mvc.perform(put(url()).header("Authorization",auth(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"No guardar\",\"id_subcategoria\":"+foreignSub.getIdSubcategoria()+"}")).andExpect(status().isNotFound());
  assertThat(em.find(Producto.class,product.getIdProducto()).getNombre()).isEqualTo("Arroz");
 }
 @Test void multipartChecksPermissionsBeforeWritingFileAndRollsBackAllFields() throws Exception {
  permit("product:update","product:update:nombre");
  MockMultipartFile file=new MockMultipartFile("imagen","demo.png","image/png",new byte[]{1,2,3});
  mvc.perform(multipart(url()+"/con-imagen").file(file).param("datos","{\"nombre\":\"No guardar\"}").with(request->{request.setMethod("PUT");return request;}).header("Authorization",auth(clerk))).andExpect(status().isForbidden());
  verifyNoInteractions(images);assertThat(em.find(Producto.class,product.getIdProducto()).getNombre()).isEqualTo("Arroz");
  when(images.save(any())).thenReturn("/media/products/fixture.png");
  mvc.perform(multipart(url()+"/con-imagen").file(file).param("datos","{\"nombre\":\"Con imagen\",\"costo\":null}").with(request->{request.setMethod("PUT");return request;}).header("Authorization",auth(owner))).andExpect(status().isOk());
  Producto stored=em.find(Producto.class,product.getIdProducto());assertThat(stored.getNombre()).isEqualTo("Con imagen");assertThat(stored.getCosto()).isNull();assertThat(stored.getImagen()).isEqualTo("/media/products/fixture.png");
 }
 @Test void rollbackRemovesNewImageAndPreservesProductData(){
  when(images.save(any())).thenReturn("/media/products/rollback.png");
  tx.executeWithoutResult(status->{
   var payload=new com.pos.productos.dto.ProductoDtos.ProductoUpdate();payload.setNombre("No persistir");
   products.actualizarConImagen(owner,product.getIdProducto(),payload,new MockMultipartFile("imagen","demo.png","image/png",new byte[]{1}));
   status.setRollbackOnly();
  });
  verify(images).remove("/media/products/rollback.png");assertThat(em.find(Producto.class,product.getIdProducto()).getNombre()).isEqualTo("Arroz");
 }
 @Test void validatesUnknownKeysAndMoneyWithoutPartialChanges() throws Exception {
  for(String extra:List.of("\"stock\":300","\"precio\":null","\"precio\":-1","\"precio\":1.234","\"nombre\":\"\"")) {
   mvc.perform(put(url()).header("Authorization",auth(owner)).contentType(MediaType.APPLICATION_JSON).content("{"+extra+"}")).andExpect(status().isBadRequest());
  }
  assertThat(em.find(Producto.class,product.getIdProducto()).getPrecio()).isEqualByComparingTo("20");
 }
 @Test void serverCsvUsesCurrentGrantsAndPreventsFormulaInjection() throws Exception {
  permit("product:read","product:read:nombre","product:read:costo");
  tx.executeWithoutResult(t->{em.find(Producto.class,product.getIdProducto()).setNombre("=SUM(1;1)");});
  String endpoint="/api/empresas/"+company.getIdEmpresa()+"/productos/exportar";
  String body="{\"columnas\":[\"nombre\",\"costo\",\"precio\"],\"ids\":["+product.getIdProducto()+","+foreignProduct.getIdProducto()+"]}";
  String csv=mvc.perform(post(endpoint).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(csv).contains("Costo de compra","'=SUM(1;1)").doesNotContain("Precio de venta");
  permit("product:read","product:read:nombre");
  String revoked=mvc.perform(post(endpoint).header("Authorization",auth(clerk)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(revoked).doesNotContain("Costo","12.00","Precio");
 }
 @Test void roleRevocationAffectsSameJwtAndCatalogueExposesActualFields() throws Exception {
  permit("product:read","product:read:costo");String bearer=auth(clerk);
  mvc.perform(get(url()).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.costo").value(12));
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner,null,List.of()));
  roleService.editar(role.getIdRol(),new RolUpdateRequest(false,null));
  mvc.perform(get(url()).header("Authorization",bearer)).andExpect(status().isNotFound());
  mvc.perform(get("/api/empresas/permisos-por-modulo").header("Authorization",auth(owner))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("product:create:imagen")));
 }
 private Usuario user(){String key=UUID.randomUUID().toString().substring(0,18);Persona p=new Persona();p.setNombreCompleto("Fixture");p.setDocumento(key);p.setFechaNacimiento(LocalDate.of(2000,1,1));p.setGenero("Otro");p.setTelefono("70000000");em.persist(p);Usuario u=new Usuario();u.setPersona(p);u.setEmail(key+"@product.test");u.setContrasena("fixture");u.setActivo(true);u.setFechaCreacion(LocalDate.now());em.persist(u);return u;}
 private Empresa company(){String key=UUID.randomUUID().toString();Empresa e=new Empresa();e.setNombre("Empresa");e.setRazonSocial("Empresa");e.setNit(key.substring(0,20));e.setCorreo(key+"@product.test");e.setFechaCreacion(LocalDate.now());em.persist(e);HistorialSuscripcion h=new HistorialSuscripcion();h.setEmpresa(e);h.setPlan(em.createQuery("select p from Plan p where p.nombre='Pro'",Plan.class).getSingleResult());h.setEstado("activo");h.setFechaInicio(LocalDate.now());h.setFechaFin(LocalDate.now().plusDays(30));em.persist(h);return e;}
 private SubcategoriaProducto sub(Empresa e){CategoriaProducto c=new CategoriaProducto();c.setIdEmpresa(e.getIdEmpresa());c.setNombre(UUID.randomUUID().toString());em.persist(c);SubcategoriaProducto s=new SubcategoriaProducto();s.setCategoriaProducto(c);s.setNombre("Bebidas");em.persist(s);return s;}
 private Producto product(Empresa e,SubcategoriaProducto s){Producto p=new Producto();p.setIdEmpresa(e.getIdEmpresa());p.setSubcategoria(s);p.setNombre("Arroz");p.setDescripcion("Original");p.setUnidadMedida("Unidad");p.setPrecio(new BigDecimal("20.00"));p.setCosto(new BigDecimal("12.00"));p.setImagen("/media/products/private.png");em.persist(p);return p;}
 private void assign(Usuario u,Rol r,Empresa e){UsuarioRol a=new UsuarioRol();a.setUsuario(u);a.setRol(r);a.setIdEmpresa(e.getIdEmpresa());a.setFecha(LocalDate.now());em.persist(a);}
}
