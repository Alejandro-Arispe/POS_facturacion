package com.pos.operaciones;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;
import com.pos.common.security.JwtService;
import com.pos.common.access.RbacMigration;
import com.pos.empresas.domain.*;
import com.pos.productos.domain.*;
import com.pos.inventario.domain.*;
import com.pos.clientes.domain.*;
import com.pos.ventas.domain.*;
import com.pos.usuarios.domain.*;
import com.pos.usuarios.repo.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pos-rbac;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","app.superadmin.email=","app.superadmin.password=","app.ai.gemini.api-key="})
@AutoConfigureMockMvc
class PosOperationsRbacTests {
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  com.pos.support.RbacTestDatabase.configure(registry,"operations",false);
 }
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tm; @Autowired JwtService jwt;
    @Autowired RolRepository roles; @Autowired PermisoRepository permissions; @Autowired RolPermisoRepository grants;
    @Autowired RbacMigration migration;
    @MockitoBean com.pos.common.mail.EmailService mail;
    @MockitoBean com.pos.auditoria.CajaNegraWriter audit;
    @MockitoBean com.pos.notifications.service.NotificationService notifications;
    TransactionTemplate tx; Usuario owner,staff,customer; Empresa company,other; Sucursal a,b,foreign;
    Caja cash; CajaSesion session,sessionB; Rol role; Producto product,foreignProduct; Cliente client,foreignClient;
    Integer payment,saleType,entryType,exitType,cashIncome,cashOpen;

    @BeforeEach void setup() {
        tx=new TransactionTemplate(tm);
        tx.executeWithoutResult(t -> {
            owner=user();staff=user();customer=user();company=company();other=company();a=branch(company);b=branch(company);foreign=branch(other);
            role=new Rol();role.setNombre("Operador");role.setDescripcion("Fixture");role.setTipo("CUSTOM");role.setIdEmpresa(company.getIdEmpresa());em.persist(role);
            assign(owner,roles.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow(),company,null);
            assign(staff,role,company,a.getIdSucursal());
            assign(customer,roles.findByNombreIgnoreCase("CLIENTE").orElseThrow(),company,null);
            product=product(company);foreignProduct=product(other);stock(product,a,100);stock(product,b,100);
            cash=cash(a);session=session(cash,staff);sessionB=session(cash(b),staff);
            payment=em.createQuery("select m from MetodoPago m where upper(m.nombre)='EFECTIVO'",MetodoPago.class).getSingleResult().getIdMetodoPago();
            saleType=em.createQuery("select t from TipoVenta t where upper(t.nombre)='CONTADO'",TipoVenta.class).getSingleResult().getIdTipoVenta();
            entryType=em.createQuery("select t from TipoMovimiento t where lower(t.direccion)='entrada'",TipoMovimiento.class).setMaxResults(1).getSingleResult().getIdTipoMovimiento();
            exitType=em.createQuery("select t from TipoMovimiento t where lower(t.nombre)='venta'",TipoMovimiento.class).getSingleResult().getIdTipoMovimiento();
            cashIncome=em.createQuery("select t from TipoMovimientoCaja t where t.nombre='INGRESO'",TipoMovimientoCaja.class).getSingleResult().getIdTipoMovimientoCaja();
            cashOpen=em.createQuery("select t from TipoMovimientoCaja t where t.nombre='APERTURA'",TipoMovimientoCaja.class).getSingleResult().getIdTipoMovimientoCaja();
            client=client(company,customer);foreignClient=client(other,customer);
        });
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    String auth(Usuario user){return "Bearer "+jwt.createAccessToken(user.getIdUsuario(),user.getEmail(),List.of("ADMINISTRADOR"));}
    void permit(String... codes){tx.executeWithoutResult(t -> {grants.deleteAll(grants.findByRol_IdRol(role.getIdRol()));grants.flush();for(String code:codes){RolPermiso g=new RolPermiso();g.setRol(em.getReference(Rol.class,role.getIdRol()));g.setPermiso(permissions.findByCodigoCanonico(code).orElseThrow());em.persist(g);}});}
    ResultActions postJson(String url,Usuario user,Object body)throws Exception{return mvc.perform(post(url).header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
    ResultActions putJson(String url,Usuario user,Object body)throws Exception{return mvc.perform(put(url).header("Authorization",auth(user)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));}
    ResultActions getJson(String url,Usuario user)throws Exception{return mvc.perform(get(url).header("Authorization",auth(user)));}
    String saleUrl(){return "/api/ventas/sesiones/"+session.getIdCajaSesion()+"/ventas";}
    String inv(Sucursal branch){return "/api/inventario/empresas/"+company.getIdEmpresa()+"/sucursales/"+branch.getIdSucursal();}
    String movementUrl(){return "/api/cajas/sesiones/"+session.getIdCajaSesion()+"/movimientos";}
    String closeUrl(){return "/api/cajas/sesiones/"+session.getIdCajaSesion()+"/cierres";}
    Map<String,Object> sale(){var body=new LinkedHashMap<String,Object>();body.put("id_tipo_venta",saleType);body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",2)));body.put("pagos",List.of(Map.of("id_metodo_pago",payment,"monto",40)));return body;}
    Map<String,Object> cashMovement(Integer type,int amount){return Map.of("id_tipo_movimiento_caja",type,"id_metodo_pago",payment,"monto",amount,"concepto","Prueba");}
    Map<String,Object> inventoryMovement(Integer type,int quantity,Integer productId){return Map.of("id_tipo_movimiento",type,"id_producto",productId,"cantidad",quantity);}
    Map<String,Object> closing(int expected,int real,int difference){return Map.of("id_metodo_pago",payment,"monto_esperado",expected,"monto_real",real,"diferencia",difference);}
    long count(Class<?> entity){return em.createQuery("select count(e) from "+entity.getSimpleName()+" e",Long.class).getSingleResult();}
    int stockAmount(){return em.createQuery("select s.cantidad from Stock s where s.producto.idProducto=:p and s.idSucursal=:b",Integer.class).setParameter("p",product.getIdProducto()).setParameter("b",a.getIdSucursal()).getSingleResult();}

    @Test void noPermissionsDenyAllOperationalEndpoints() throws Exception {
        getJson(saleUrl(),staff).andExpect(status().isForbidden());postJson(saleUrl(),staff,sale()).andExpect(status().isForbidden());
        getJson(inv(a)+"/stock",staff).andExpect(status().isForbidden());getJson(inv(a)+"/movimientos",staff).andExpect(status().isForbidden());
        postJson(inv(a)+"/movimientos",staff,inventoryMovement(entryType,1,product.getIdProducto())).andExpect(status().isForbidden());
        postJson(movementUrl(),staff,cashMovement(cashIncome,5)).andExpect(status().isForbidden());getJson(movementUrl(),staff).andExpect(status().isForbidden());
        postJson(closeUrl(),staff,List.of(closing(0,0,0))).andExpect(status().isForbidden());
        postJson("/api/cajas/"+cash.getIdCaja()+"/sesiones",staff,Map.of("monto_inicial",0)).andExpect(status().isForbidden());
    }
    @Test void saleComputesTotalsAndItsDerivedMovementsWithoutManualInventoryPermission() throws Exception {
        permit("sale:create","sale:read");long sales=count(Venta.class),movements=count(MovimientoInventario.class),money=count(MovimientoCaja.class);
        postJson(saleUrl(),staff,sale()).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(40)).andExpect(jsonPath("$.total").value(40)).andExpect(jsonPath("$.estado").value("PAGADA"));
        assertThat(count(Venta.class)).isEqualTo(sales+1);assertThat(count(MovimientoInventario.class)).isEqualTo(movements+1);assertThat(count(MovimientoCaja.class)).isEqualTo(money+1);assertThat(stockAmount()).isEqualTo(98);
        getJson(saleUrl(),staff).andExpect(status().isOk()).andExpect(jsonPath("$[0].total").value(40));
    }
    @Test void forgedTotalsAndPaymentsNeverPersistPartialSale() throws Exception {
        permit("sale:create");long sales=count(Venta.class),money=count(MovimientoCaja.class);
        for(String key:List.of("subtotal","total")){var body=sale();body.put(key,1);postJson(saleUrl(),staff,body).andExpect(status().isBadRequest());}
        var wrong=sale();wrong.put("pagos",List.of(Map.of("id_metodo_pago",payment,"monto",1)));postJson(saleUrl(),staff,wrong).andExpect(status().isBadRequest());
        assertThat(count(Venta.class)).isEqualTo(sales);assertThat(count(MovimientoCaja.class)).isEqualTo(money);assertThat(stockAmount()).isEqualTo(100);
    }
    @Test void protectedPriceAndDiscountAreIndependentAndRejectWholeOperation() throws Exception {
        permit("sale:create");long before=count(Venta.class);var body=sale();body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",2,"precio_unitario",10)));
        postJson(saleUrl(),staff,body).andExpect(status().isForbidden());
        body=sale();body.put("descuento_total",5);body.put("total",35);postJson(saleUrl(),staff,body).andExpect(status().isForbidden());
        permit("sale:create","sale:discount");postJson(saleUrl(),staff,body).andExpect(status().isForbidden());
        permit("sale:create","sale:create:descuento");postJson(saleUrl(),staff,body).andExpect(status().isForbidden());
        assertThat(count(Venta.class)).isEqualTo(before);assertThat(stockAmount()).isEqualTo(100);
    }
    @Test void authorizedCustomPriceAndDiscountProduceServerAmounts() throws Exception {
        permit("sale:create","sale:create:precio_unitario","sale:create:descuento","sale:discount");var body=sale();
        body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",2,"precio_unitario",18,"descuento",3,"subtotal",33)));body.put("descuento_total",4);body.put("pagos",List.of(Map.of("id_metodo_pago",payment,"monto",32)));
        postJson(saleUrl(),staff,body).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(36)).andExpect(jsonPath("$.descuento_total").value(4)).andExpect(jsonPath("$.total").value(32)).andExpect(jsonPath("$.detalles[0].total").value(33));
    }
    @Test void invalidQuantitiesMoneyInactiveProductsAndDuplicateLinesAreRejected() throws Exception {
        permit("sale:create","sale:create:precio_unitario","sale:create:descuento","sale:discount");
        for(int qty:List.of(0,-1)){var body=sale();body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",qty)));postJson(saleUrl(),staff,body).andExpect(status().isBadRequest());}
        for(String price:List.of("-1","1.001","100000000000")){var body=sale();body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",2,"precio_unitario",new BigDecimal(price))));postJson(saleUrl(),staff,body).andExpect(status().isBadRequest());}
        var body=sale();body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",1),Map.of("id_producto",product.getIdProducto(),"cantidad",1)));postJson(saleUrl(),staff,body).andExpect(status().isBadRequest());
        tx.executeWithoutResult(t -> em.find(Producto.class,product.getIdProducto()).setActivo(false));postJson(saleUrl(),staff,sale()).andExpect(status().isBadRequest());assertThat(stockAmount()).isEqualTo(100);
    }
    @Test void insufficientStockRollsBackSaleDetailsPaymentsAndInventory() throws Exception {
        permit("sale:create");tx.executeWithoutResult(t -> em.createQuery("update Stock s set s.cantidad=1 where s.producto.idProducto=:p").setParameter("p",product.getIdProducto()).executeUpdate());
        long sales=count(Venta.class),details=count(DetalleVenta.class),money=count(MovimientoCaja.class),moves=count(MovimientoInventario.class);
        postJson(saleUrl(),staff,sale()).andExpect(status().isBadRequest());assertThat(count(Venta.class)).isEqualTo(sales);assertThat(count(DetalleVenta.class)).isEqualTo(details);assertThat(count(MovimientoCaja.class)).isEqualTo(money);assertThat(count(MovimientoInventario.class)).isEqualTo(moves);assertThat(stockAmount()).isEqualTo(1);
    }
    @Test void foreignProductsClientsAndSiblingBranchesAreDenied() throws Exception {
        permit("sale:create","sale:read","inventory:read","inventory_movement:create","cash_movement:read");
        var body=sale();body.put("detalles",List.of(Map.of("id_producto",foreignProduct.getIdProducto(),"cantidad",2)));postJson(saleUrl(),staff,body).andExpect(status().isNotFound());
        body=sale();body.put("id_cliente",foreignClient.getIdCliente());postJson(saleUrl(),staff,body).andExpect(status().isNotFound());
        getJson(inv(b)+"/stock",staff).andExpect(status().isNotFound());getJson("/api/ventas/sesiones/"+sessionB.getIdCajaSesion()+"/ventas",staff).andExpect(status().isNotFound());getJson("/api/cajas/sesiones/"+sessionB.getIdCajaSesion()+"/movimientos",staff).andExpect(status().isNotFound());
    }
    @Test void authorizationRevocationAppliesToExistingToken() throws Exception {
        permit("sale:create","sale:read");String bearer=auth(staff);
        mvc.perform(get(saleUrl()).header("Authorization",bearer)).andExpect(status().isOk());permit("sale:read");
        mvc.perform(post(saleUrl()).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(sale()))).andExpect(status().isForbidden());
        tx.executeWithoutResult(t -> em.find(Rol.class,role.getIdRol()).setActivo(false));mvc.perform(get(saleUrl()).header("Authorization",bearer)).andExpect(status().isNotFound());
    }
    @Test void closedSessionAndDifferentOperatorCannotWrite() throws Exception {
        permit("sale:create","cash_movement:create","cash_register:close");postJson(movementUrl(),owner,cashMovement(cashIncome,1)).andExpect(status().isForbidden());postJson(closeUrl(),owner,List.of(closing(0,0,0))).andExpect(status().isForbidden());
        tx.executeWithoutResult(t -> em.find(CajaSesion.class,session.getIdCajaSesion()).setEstado("Cerrado"));postJson(saleUrl(),staff,sale()).andExpect(status().isBadRequest());postJson(movementUrl(),staff,cashMovement(cashIncome,1)).andExpect(status().isBadRequest());
    }
    @Test void cashMovementsHaveDedicatedPermissionAndReservedTypesCannotBeForged() throws Exception {
        permit("inventory_movement:create");postJson(movementUrl(),staff,cashMovement(cashIncome,5)).andExpect(status().isForbidden());
        permit("cash_movement:create","cash_movement:read");postJson(movementUrl(),staff,cashMovement(cashOpen,5)).andExpect(status().isBadRequest());postJson(movementUrl(),staff,cashMovement(cashIncome,-5)).andExpect(status().isBadRequest());
        postJson(movementUrl(),staff,cashMovement(cashIncome,5)).andExpect(status().isOk());getJson(movementUrl()+"/resumen",staff).andExpect(status().isOk()).andExpect(jsonPath("$.monto_esperado_total").value(5));
    }
    @Test void closingRecomputesExpectedAndDifferenceAndRejectsDuplicateMethods() throws Exception {
        permit("cash_movement:create","cash_register:close");postJson(movementUrl(),staff,cashMovement(cashIncome,30)).andExpect(status().isOk());long closures=count(CajaCierreDetalle.class);
        postJson(closeUrl(),staff,List.of(closing(1,30,29))).andExpect(status().isBadRequest());postJson(closeUrl(),staff,List.of(closing(30,28,100))).andExpect(status().isBadRequest());postJson(closeUrl(),staff,List.of(closing(30,28,-2),closing(30,28,-2))).andExpect(status().isBadRequest());
        assertThat(count(CajaCierreDetalle.class)).isEqualTo(closures);
        postJson(closeUrl(),staff,List.of(closing(30,28,-2))).andExpect(status().isOk()).andExpect(jsonPath("$.monto_final").value(30)).andExpect(jsonPath("$.cierres[0].diferencia").value(-2));
        assertThat(em.find(CajaSesion.class,session.getIdCajaSesion()).getEstado()).isEqualTo("Cerrado");postJson(closeUrl(),staff,List.of(closing(30,28,-2))).andExpect(status().isBadRequest());
    }
    @Test void openingRequiresItsPermissionAndCreatesAnAtomicOpeningMovement() throws Exception {
        permit("cash_register:open");tx.executeWithoutResult(t -> {em.find(CajaSesion.class,session.getIdCajaSesion()).setEstado("Cerrado");em.find(CajaSesion.class,sessionB.getIdCajaSesion()).setEstado("Cerrado");});
        long before=count(MovimientoCaja.class);postJson("/api/cajas/"+cash.getIdCaja()+"/sesiones",staff,Map.of("monto_inicial",-1)).andExpect(status().isBadRequest());
        postJson("/api/cajas/"+cash.getIdCaja()+"/sesiones",staff,Map.of("monto_inicial",25)).andExpect(status().isOk()).andExpect(jsonPath("$.monto_inicial").value(25));assertThat(count(MovimientoCaja.class)).isEqualTo(before+1);
    }
    @Test void stockActionsAreIndependentAndCannotForgeAutomaticSaleOutflow() throws Exception {
        permit("inventory:read");getJson(inv(a)+"/stock",staff).andExpect(status().isOk());postJson(inv(a)+"/movimientos",staff,inventoryMovement(entryType,4,product.getIdProducto())).andExpect(status().isForbidden());
        permit("inventory_movement:create","inventory_movement:read");postJson(inv(a)+"/movimientos",staff,inventoryMovement(exitType,2,product.getIdProducto())).andExpect(status().isBadRequest());postJson(inv(a)+"/movimientos",staff,inventoryMovement(entryType,4,foreignProduct.getIdProducto())).andExpect(status().isNotFound());
        postJson(inv(a)+"/movimientos",staff,inventoryMovement(entryType,4,product.getIdProducto())).andExpect(status().isOk());assertThat(stockAmount()).isEqualTo(104);getJson(inv(a)+"/movimientos",staff).andExpect(status().isOk());
    }
    @Test void stockLimitsRequireConfigureAndValidateTheirRelationship() throws Exception {
        String url=inv(a)+"/stock/"+product.getIdProducto();permit("inventory:read");putJson(url,staff,Map.of("stock_minimo",5,"stock_maximo",10)).andExpect(status().isForbidden());
        permit("inventory:configure");putJson(url,staff,Map.of("stock_minimo",5,"cantidad",999)).andExpect(status().isBadRequest());putJson(url,staff,Map.of("stock_minimo",20,"stock_maximo",10)).andExpect(status().isBadRequest());putJson(url,staff,Map.of("stock_minimo",5,"stock_maximo",10)).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(100));
    }
    @Test void companyEditingRequiresCompanyScopeAndCannotBorrowBranchPermission() throws Exception {
        permit("company:update");var body=Map.of("nombre","Cambio","razon_social","Cambio","nit","123","correo","demo@example.invalid","activo",true);
        putJson("/api/empresas/"+company.getIdEmpresa(),staff,body).andExpect(status().isForbidden());putJson("/api/empresas/"+company.getIdEmpresa(),owner,body).andExpect(status().isOk());
    }
    @Test void clientsAndCategoriesRequireTheirOwnActionsAndCannotChangeOtherTenant() throws Exception {
        getJson("/api/empresas/"+company.getIdEmpresa()+"/clientes",staff).andExpect(status().isForbidden());
        permit("client:read","client:update","client_category:create");getJson("/api/empresas/"+company.getIdEmpresa()+"/clientes",staff).andExpect(status().isOk());
        putJson("/api/empresas/"+company.getIdEmpresa()+"/clientes/"+foreignClient.getIdCliente(),staff,Map.of("activo",false)).andExpect(status().isNotFound());
        putJson("/api/empresas/"+company.getIdEmpresa()+"/clientes/"+client.getIdCliente(),staff,Map.of("saldo_credito",999)).andExpect(status().isBadRequest());
        postJson("/api/categorias-cliente/"+company.getIdEmpresa(),staff,Map.of("nombre","Categoría","descuento_base",101)).andExpect(status().isBadRequest());
        postJson("/api/categorias-cliente/"+company.getIdEmpresa(),staff,Map.of("nombre","Categoría","descuento_base",10)).andExpect(status().isOk());
    }
    @Test void clientInvitationUsesSingleUseTokenAndCannotAuthenticateAsAUser() throws Exception {
        Usuario invited=tx.execute(t -> user());String endpoint="/api/empresas/"+company.getIdEmpresa()+"/invitar-cliente";
        postJson(endpoint,staff,Map.of("email",invited.getEmail())).andExpect(status().isForbidden());
        String response=postJson(endpoint,owner,Map.of("email",invited.getEmail())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String link=json.readTree(response).get("link_invitacion").asText();String token=link.substring(link.lastIndexOf('/')+1);
        mvc.perform(get("/api/empresas/mis-empresas").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/invitaciones/cliente/aceptar/"+token)).andExpect(status().isOk());mvc.perform(get("/api/invitaciones/cliente/aceptar/"+token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/invitaciones/cliente/aceptar/"+company.getIdEmpresa()+"/"+invited.getIdUsuario())).andExpect(status().isNotFound());
    }
    @Test void invitationCannotBeAcceptedAfterIssuerLosesPermission() throws Exception {
        tx.executeWithoutResult(t -> {var assignment=em.createQuery("select u from UsuarioRol u where u.usuario.idUsuario=:u and u.rol.idRol=:r",UsuarioRol.class).setParameter("u",staff.getIdUsuario()).setParameter("r",role.getIdRol()).getSingleResult();assignment.setIdSucursal(null);});permit("client:create");Usuario invited=tx.execute(t -> user());
        String response=postJson("/api/empresas/"+company.getIdEmpresa()+"/invitar-cliente",staff,Map.of("email",invited.getEmail())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String link=json.readTree(response).get("link_invitacion").asText();
        permit();mvc.perform(get(link.substring(link.indexOf("/api/")))).andExpect(status().isForbidden());
    }
    @Test void ordersFilterBranchesAndCannotBeConvertedWithoutSale() throws Exception {
        PedidoCliente orderA=tx.execute(t -> order(a)),orderB=tx.execute(t -> order(b));permit("order:read","order:manage");String base="/api/pedidos/empresas/"+company.getIdEmpresa();
        getJson(base,staff).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));getJson(base+"/"+orderB.getIdPedido(),staff).andExpect(status().isNotFound());
        mvc.perform(patch(base+"/"+orderA.getIdPedido()+"/estado").header("Authorization",auth(staff)).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"convertido_en_venta\"}")).andExpect(status().isBadRequest());
        getJson(base,customer).andExpect(status().isForbidden());getJson("/api/pedidos/me/"+orderA.getIdPedido(),customer).andExpect(status().isOk());getJson("/api/pedidos/me/"+orderA.getIdPedido(),staff).andExpect(status().isNotFound());
    }
    @Test void v3DoesNotRestoreRevokedCommercialGrants() {
        permit("sale:create","sale:create:descuento","sale:discount");tx.executeWithoutResult(t -> {var grant=grants.findByRol_IdRol(role.getIdRol()).stream().filter(g -> "sale:create:descuento".equals(g.getPermiso().getCodigoCanonico())).findFirst().orElseThrow();grant.setActivo(false);});tx.executeWithoutResult(t -> migration.migrate());
        assertThat(grants.findConcesiones(List.of(role.getIdRol()))).noneMatch(g -> "sale:create:descuento".equals(g.getPermiso().getCodigoCanonico()));
    }
    @Test void existingCreditCollectionRequiresItsDedicatedAction() throws Exception {
        permit("sale:create");var body=sale();Integer credit=em.createQuery("select t.idTipoVenta from TipoVenta t where upper(t.nombre) in ('CREDITO','CRÉDITO')",Integer.class).setMaxResults(1).getSingleResult();
        body.put("id_tipo_venta",credit);body.put("id_cliente",client.getIdCliente());body.remove("pagos");
        String response=postJson(saleUrl(),staff,body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();Integer saleId=json.readTree(response).get("id_venta").asInt();
        Integer account=em.createQuery("select c.idCxc from CuentaPorCobrar c where c.venta.idVenta=:id",Integer.class).setParameter("id",saleId).getSingleResult();
        String endpoint="/api/ventas/sesiones/"+session.getIdCajaSesion()+"/pagos-credito";
        var paymentBody=Map.of("id_cxc",account,"pagos_credito",List.of(Map.of("id_metodo_pago",payment,"monto_pagado",5)));
        postJson(endpoint,staff,paymentBody).andExpect(status().isForbidden());permit("sale:collect");
        postJson(endpoint,staff,paymentBody).andExpect(status().isOk()).andExpect(jsonPath("$.saldo_pendiente").value(35));
    }
    @Test void creditLimitIsEnforcedBeforePersistingAnything() throws Exception {
        permit("sale:create");var body=sale();Integer credit=em.createQuery("select t.idTipoVenta from TipoVenta t where upper(t.nombre) in ('CREDITO','CRÉDITO')",Integer.class).setMaxResults(1).getSingleResult();
        body.put("id_tipo_venta",credit);body.put("id_cliente",client.getIdCliente());body.remove("pagos");body.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",6)));
        long before=count(CuentaPorCobrar.class);postJson(saleUrl(),staff,body).andExpect(status().isBadRequest());assertThat(count(CuentaPorCobrar.class)).isEqualTo(before);assertThat(stockAmount()).isEqualTo(100);
    }
    @Test void orderConversionChecksItsDetailsAndCannotBeRepeated() throws Exception {
        PedidoCliente order=tx.execute(t -> {PedidoCliente o=order(a);PedidoClienteDetalle d=new PedidoClienteDetalle();d.setPedido(o);d.setIdProducto(product.getIdProducto());d.setCantidad(2);d.setPrecioUnitarioEstimado(new BigDecimal("20.00"));d.setDescuentoEstimado(BigDecimal.ZERO);d.setSubtotalEstimado(new BigDecimal("40.00"));em.persist(d);return o;});
        permit("sale:create","order:manage");var body=sale();body.put("id_pedido",order.getIdPedido());body.put("id_cliente",client.getIdCliente());
        var invalid=new LinkedHashMap<>(body);invalid.put("detalles",List.of(Map.of("id_producto",product.getIdProducto(),"cantidad",1)));invalid.put("pagos",List.of(Map.of("id_metodo_pago",payment,"monto",20)));
        postJson(saleUrl(),staff,invalid).andExpect(status().isBadRequest());postJson(saleUrl(),staff,body).andExpect(status().isOk());
        PedidoCliente stored=em.find(PedidoCliente.class,order.getIdPedido());assertThat(stored.getEstado()).isEqualTo("convertido_en_venta");assertThat(stored.getIdVenta()).isNotNull();
        postJson(saleUrl(),staff,body).andExpect(status().isConflict());assertThat(stockAmount()).isEqualTo(98);
    }
    @Test void rootProductListSkipsUnreadableCompanyWithoutRollingBack() throws Exception {
        permit("product:read","product:read:nombre");tx.executeWithoutResult(t -> assign(staff,roles.findByNombreIgnoreCase("CLIENTE").orElseThrow(),other,null));
        getJson("/api/productos",staff).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id_empresa").value(company.getIdEmpresa()));
    }

    private Usuario user(){String key=UUID.randomUUID().toString().substring(0,18);Persona p=new Persona();p.setNombreCompleto("Fixture");p.setDocumento(key);p.setFechaNacimiento(LocalDate.of(2000,1,1));p.setGenero("Otro");p.setTelefono("70000000");em.persist(p);Usuario u=new Usuario();u.setPersona(p);u.setEmail(key+"@ops.test");u.setContrasena("fixture");u.setActivo(true);u.setFechaCreacion(LocalDate.now());em.persist(u);return u;}
    private Empresa company(){String key=UUID.randomUUID().toString();Empresa e=new Empresa();e.setNombre("Empresa");e.setRazonSocial("Empresa");e.setNit(key.substring(0,20));e.setCorreo(key+"@ops.test");e.setFechaCreacion(LocalDate.now());em.persist(e);HistorialSuscripcion h=new HistorialSuscripcion();h.setEmpresa(e);h.setPlan(em.createQuery("select p from Plan p where p.nombre='Pro'",Plan.class).getSingleResult());h.setEstado("activo");h.setFechaInicio(LocalDate.now());h.setFechaFin(LocalDate.now().plusDays(30));em.persist(h);return e;}
    private Sucursal branch(Empresa company){Sucursal s=new Sucursal();s.setEmpresa(company);s.setNombre("Sucursal");s.setDireccion("Centro");s.setTelefono("70000000");s.setCiudad("La Paz");s.setFechaRegistro(LocalDate.now());em.persist(s);return s;}
    private Caja cash(Sucursal branch){Caja c=new Caja();c.setSucursal(branch);c.setNombre("Caja");c.setCodigo(UUID.randomUUID().toString().substring(0,15));c.setFechaCreacion(LocalDate.now());em.persist(c);return c;}
    private CajaSesion session(Caja cash,Usuario user){CajaSesion s=new CajaSesion();s.setCaja(cash);s.setIdUsuario(user.getIdUsuario());s.setFechaApertura(LocalDateTime.now());s.setMontoInicial(BigDecimal.ZERO);s.setEstado("Abierto");em.persist(s);return s;}
    private Producto product(Empresa company){CategoriaProducto c=new CategoriaProducto();c.setIdEmpresa(company.getIdEmpresa());c.setNombre(UUID.randomUUID().toString());em.persist(c);SubcategoriaProducto sub=new SubcategoriaProducto();sub.setCategoriaProducto(c);sub.setNombre("General");em.persist(sub);Producto p=new Producto();p.setIdEmpresa(company.getIdEmpresa());p.setSubcategoria(sub);p.setNombre("Arroz");p.setUnidadMedida("Unidad");p.setPrecio(new BigDecimal("20.00"));p.setCosto(new BigDecimal("12.00"));em.persist(p);return p;}
    private void stock(Producto product,Sucursal branch,int amount){Stock s=new Stock();s.setProducto(product);s.setIdSucursal(branch.getIdSucursal());s.setCantidad(amount);s.setStockMinimo(0);s.setStockMaximo(1000);s.setFechaActualizacion(LocalDateTime.now());em.persist(s);}
    private Cliente client(Empresa company,Usuario user){CategoriaCliente category=new CategoriaCliente();category.setIdEmpresa(company.getIdEmpresa());category.setNombre("General");category.setLimiteCredito(new BigDecimal("100.00"));em.persist(category);Cliente c=new Cliente();c.setIdUsuario(user.getIdUsuario());c.setIdEmpresa(company.getIdEmpresa());c.setCategoriaCliente(category);c.setCodigoCliente(UUID.randomUUID().toString());em.persist(c);return c;}
    private PedidoCliente order(Sucursal branch){PedidoCliente p=new PedidoCliente();p.setIdEmpresa(company.getIdEmpresa());p.setIdSucursal(branch.getIdSucursal());p.setIdCliente(client.getIdCliente());p.setEstado("listo_para_recoger");p.setFechaCreacion(LocalDateTime.now());em.persist(p);return p;}
    private void assign(Usuario user,Rol role,Empresa company,Integer branch){UsuarioRol a=new UsuarioRol();a.setUsuario(user);a.setRol(role);a.setIdEmpresa(company.getIdEmpresa());a.setIdSucursal(branch);a.setFecha(LocalDate.now());em.persist(a);}
}
