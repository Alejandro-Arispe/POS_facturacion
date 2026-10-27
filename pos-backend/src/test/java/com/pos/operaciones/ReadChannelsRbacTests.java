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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:read-channels;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.config.import=","app.superadmin.email=","app.superadmin.password=","app.ai.gemini.api-key=","app.media-dir=target/rbac7-media","app.xml-dir=target/rbac7-xml","app.backup-dir=target/rbac7-backups","app.analytics.url=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class ReadChannelsRbacTests {
 @org.springframework.test.context.DynamicPropertySource
 static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
  com.pos.support.RbacTestDatabase.configure(registry,"channels",false);
 }
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tm; @Autowired JwtService jwt;
    @Autowired RolRepository roles; @Autowired PermisoRepository permissions; @Autowired RolPermisoRepository grants;
    @Autowired RbacMigration migration;
    @MockitoBean com.pos.common.mail.EmailService mail;
    @MockitoBean com.pos.auditoria.CajaNegraWriter audit;
    @Autowired com.pos.notifications.service.NotificationService notifications;
    @Autowired com.pos.websocket.AdministradorWebSocketHandler dashboardSocket;
    @Autowired com.pos.websocket.ClienteWebSocketHandler clientSocket;
    java.util.List<org.springframework.web.socket.WebSocketSession> opened=new ArrayList<>();
    static final java.util.concurrent.atomic.AtomicReference<String> analyticsInput=new java.util.concurrent.atomic.AtomicReference<>();
    static final com.sun.net.httpserver.HttpServer analyticsServer=startAnalytics();
    static com.sun.net.httpserver.HttpServer startAnalytics() {
        try {
            var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/analyze",exchange -> {
                analyticsInput.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                byte[] bytes="{\"metricas\":{\"ventas\":0}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        } catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    @org.springframework.test.context.DynamicPropertySource static void analytics(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("app.analytics.url",() -> "http://127.0.0.1:"+analyticsServer.getAddress().getPort());
        registry.add("app.analytics.api-key",() -> "rbac7-fixture");
    }
    @AfterAll static void stopAnalytics(){analyticsServer.stop(0);}
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
    @AfterEach void clear(){for(var socket:opened) dashboardSocket.afterConnectionClosed(socket,org.springframework.web.socket.CloseStatus.NORMAL);SecurityContextHolder.clearContext();}
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


    Map<String,Object> query(String source,List<String> fields){var result=new LinkedHashMap<String,Object>();result.put("fuente",source);result.put("campos",fields);result.put("limite",100);return result;}
    String qbe(){return "/api/reportes/"+company.getIdEmpresa()+"/qbe/ejecutar";}
    void companyScope(){tx.executeWithoutResult(t -> {var assignment=em.createQuery("select a from UsuarioRol a where a.usuario.idUsuario=:u and a.rol.idRol=:r",UsuarioRol.class).setParameter("u",staff.getIdUsuario()).setParameter("r",role.getIdRol()).getSingleResult();assignment.setIdSucursal(null);});}
    @Test void qbeHidesCostMetadataAndDeniesExplicitReadFilterGroupAggregateAndOrder()throws Exception {
        permit("report:generate","product:read","product:read:nombre");
        getJson("/api/reportes/"+company.getIdEmpresa()+"/qbe/fuentes",staff).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Costo de compra"))));
        postJson(qbe(),staff,query("productos",List.of("costo"))).andExpect(status().isForbidden());
        var filter=query("productos",List.of("producto"));filter.put("condiciones",List.of(Map.of("campo","costo","operador","mayor_igual","valor","10")));postJson(qbe(),staff,filter).andExpect(status().isForbidden());
        var group=query("productos",List.of("producto"));group.put("agrupar",List.of("costo"));postJson(qbe(),staff,group).andExpect(status().isForbidden());
        var aggregate=query("productos",List.of("producto"));aggregate.put("agrupar",List.of("producto"));aggregate.put("agregados",List.of(Map.of("campo","costo","funcion","SUMA")));postJson(qbe(),staff,aggregate).andExpect(status().isForbidden());
        var order=query("productos",List.of("producto"));order.put("orden",Map.of("campo","costo","direccion","asc"));postJson(qbe(),staff,order).andExpect(status().isForbidden());
    }
    @Test void authorizedCostIsAvailableAndRevocationAffectsSameJwt()throws Exception {
        permit("report:generate","product:read","product:read:nombre","product:read:costo");String bearer=auth(staff);String request=json.writeValueAsString(query("productos",List.of("producto","costo")));
        mvc.perform(post(qbe()).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andExpect(jsonPath("$.filas[0][1]").value(12));
        permit("report:generate","product:read","product:read:nombre");mvc.perform(post(qbe()).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isForbidden());
    }
    @Test void inventoryValuationRequiresPriceEvenWhenOnlyUsedAsACondition()throws Exception {
        permit("report:generate","inventory:read","product:read","product:read:nombre");var request=query("inventario",List.of("cantidad"));request.put("condiciones",List.of(Map.of("campo","valor","operador","mayor_igual","valor","1")));
        postJson(qbe(),staff,request).andExpect(status().isForbidden());
    }
    @Test void qbeReadsOnlyAssignedBranchAndCompanyClientProfile()throws Exception {
        permit("report:generate","inventory:read","client:read");postJson(qbe(),staff,query("inventario",List.of("sucursal","cantidad"))).andExpect(status().isOk()).andExpect(jsonPath("$.total_filas").value(1)).andExpect(jsonPath("$.filas[0][1]").value(100));
        var request=query("clientes",List.of("codigo"));request.put("condiciones",List.of(Map.of("campo","codigo","operador","igual","valor",foreignClient.getCodigoCliente())));postJson(qbe(),staff,request).andExpect(status().isOk()).andExpect(jsonPath("$.total_filas").value(0));
    }
    @Test void reportPermissionDoesNotGrantAccessToUnderlyingResource()throws Exception {
        companyScope();permit("report:generate");String base="/api/reportes/"+company.getIdEmpresa();
        getJson(base+"/resumenventas",staff).andExpect(status().isForbidden());getJson(base+"/estadoinventario",staff).andExpect(status().isForbidden());
        postJson(base+"/interpretar",customer,Map.of("prompt","resumen de ventas")).andExpect(status().isForbidden());
        permit("report:generate","sale:read","product:read","product:read:nombre");getJson(base+"/resumenventas",staff).andExpect(status().isOk());
    }
    @Test void globalReportsCannotBorrowBranchScopedPermissions()throws Exception {
        permit("report:generate","sale:read","product:read","product:read:nombre");getJson("/api/reportes/"+company.getIdEmpresa()+"/resumenventas",staff).andExpect(status().isForbidden());
    }
    @Test void exportRequestRequiresFreshExportAndReadAuthorization()throws Exception {
        companyScope();String url="/api/reportes/"+company.getIdEmpresa()+"/resumenventas";permit("report:generate","sale:read","product:read","product:read:nombre");
        mvc.perform(get(url).header("Authorization",auth(staff)).header("X-Report-Export","true")).andExpect(status().isForbidden());
        permit("report:generate","report:export","sale:read","product:read","product:read:nombre");mvc.perform(get(url).header("Authorization",auth(staff)).header("X-Report-Export","true")).andExpect(status().isOk());
        permit("report:generate","report:export","sale:read","product:read");mvc.perform(get(url).header("Authorization",auth(staff)).header("X-Report-Export","true")).andExpect(status().isForbidden());
    }
    @Test void exportCannotBorrowItsPermissionFromAnotherBranch()throws Exception {
        permit("report:generate","inventory:read");tx.executeWithoutResult(t -> {Rol otherRole=new Rol();otherRole.setIdEmpresa(company.getIdEmpresa());otherRole.setTipo("CUSTOM");otherRole.setNombre("Exportador");otherRole.setDescripcion("Fixture");em.persist(otherRole);RolPermiso g=new RolPermiso();g.setRol(otherRole);g.setPermiso(permissions.findByCodigoCanonico("report:export").orElseThrow());em.persist(g);assign(staff,otherRole,company,b.getIdSucursal());});
        mvc.perform(post(qbe()).header("Authorization",auth(staff)).header("X-Report-Export","true").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(query("inventario",List.of("cantidad"))))).andExpect(status().isForbidden());
    }
    @Test void stockEndpointOmitsRestrictedProductFieldsInsteadOfFabricatingValues()throws Exception {
        permit("inventory:read");getJson(inv(a)+"/stock",staff).andExpect(status().isOk()).andExpect(jsonPath("$[0].precio").doesNotHaveJsonPath()).andExpect(jsonPath("$[0].nombre_producto").doesNotHaveJsonPath()).andExpect(jsonPath("$[0].imagen").doesNotHaveJsonPath());
        permit("inventory:read","product:read","product:read:precio");getJson(inv(a)+"/stock",staff).andExpect(status().isOk()).andExpect(jsonPath("$[0].precio").value(20));
    }
    @Test void knownImageNeedsCurrentImageReadAndActiveMembership()throws Exception {
        String file=UUID.randomUUID()+".png",url="/media/products/"+file;java.nio.file.Path folder=java.nio.file.Path.of("target/rbac7-media/products");java.nio.file.Files.createDirectories(folder);java.nio.file.Files.write(folder.resolve(file),new byte[]{1,2,3});tx.executeWithoutResult(t -> em.find(Producto.class,product.getIdProducto()).setImagen(url));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());permit("product:read","product:read:nombre");getJson(url,staff).andExpect(status().isNotFound());
        permit("product:read","product:read:imagen");getJson(url,staff).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3})).andExpect(header().string("Cache-Control","no-store"));
        permit("product:read");getJson(url,staff).andExpect(status().isNotFound());getJson(url,customer).andExpect(status().isNotFound());
    }
    @Test void rawMediaAndXmlDirectoriesAreNoLongerPublic()throws Exception {
        mvc.perform(get("/XMLS/factura.xml")).andExpect(status().isUnauthorized());getJson("/XMLS/factura.xml",owner).andExpect(status().isNotFound());getJson("/media/private.json",owner).andExpect(status().isNotFound());
    }
    @Test void invoiceMetadataIsScopedAndWholeFilesNeedAllProductReadFields()throws Exception {
        Factura first=tx.execute(t -> invoice(session));tx.execute(t -> invoice(sessionB));String list="/api/empresas/"+company.getIdEmpresa()+"/facturas",url="/api/facturas/"+first.getIdFactura()+"/xml";
        getJson(list,staff).andExpect(status().isForbidden());permit("invoice:reprint","sale:read");getJson(list,staff).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].xml_generado").value(url));getJson(url,staff).andExpect(status().isForbidden());
        List<String> all=new ArrayList<>(List.of("invoice:reprint","sale:read","product:read"));com.pos.common.access.ProductFields.ALL.forEach(f -> all.add("product:read:"+f));permit(all.toArray(String[]::new));getJson(url,staff).andExpect(status().isOk()).andExpect(content().string("<factura>segura</factura>"));
        all.remove("product:read:costo");permit(all.toArray(String[]::new));getJson(url,staff).andExpect(status().isForbidden());
    }
    @Test void backupCannotRevealCostAfterItWasRevokedFromOwnerRole()throws Exception {
        Rol admin=roles.findByNombreIgnoreCase("ADMINISTRADOR").orElseThrow();Integer id=permissions.findByCodigoCanonico("product:read:costo").orElseThrow().getIdPermiso();
        tx.executeWithoutResult(t -> {for(var g:grants.findByRol_IdRol(admin.getIdRol())) if(g.getPermiso().getIdPermiso().equals(id)) g.setActivo(false);});
        try {getJson("/api/empresas/"+company.getIdEmpresa()+"/respaldos",owner).andExpect(status().isForbidden());}
        finally {tx.executeWithoutResult(t -> {for(var g:grants.findByRol_IdRol(admin.getIdRol())) if(g.getPermiso().getIdPermiso().equals(id))g.setActivo(true);});}
    }
    @Test void notificationsDoNotExposeOtherRecipientsOrAllowMarkingTheirRows()throws Exception {
        var row=tx.execute(t -> notification(customer.getIdUsuario(),Map.of("id_usuario",customer.getIdUsuario(),"id_empresa",company.getIdEmpresa())));
        getJson("/notifications/history/empresas/"+company.getIdEmpresa()+"?id_usuario="+customer.getIdUsuario(),staff).andExpect(status().isForbidden());
        postJson("/notifications/mark-read",staff,Map.of("id",row.getId())).andExpect(status().isNotFound());postJson("/notifications/mark-read",customer,Map.of("id",row.getId())).andExpect(status().isOk());
        getJson("/notifications/history/empresas/"+company.getIdEmpresa(),customer).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("COSTO_SECRETO"))));
    }
    @Test void stockNotificationsRespectBranchAndProductNameRead()throws Exception {
        tx.executeWithoutResult(t -> {notification(null,Map.of("id_producto",product.getIdProducto(),"id_sucursal",a.getIdSucursal()));notification(null,Map.of("id_producto",product.getIdProducto(),"id_sucursal",b.getIdSucursal()));});
        String url="/notifications/history/empresas/"+company.getIdEmpresa();permit("inventory_alert:read","inventory:read");getJson(url,staff).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        permit("inventory_alert:read","inventory:read","product:read","product:read:nombre");getJson(url,staff).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test void deviceRegistrationCannotSpoofAnotherAccountOrCompany()throws Exception {
        postJson("/notifications/register-token",staff,Map.of("token","device-"+UUID.randomUUID(),"uid_usuario",customer.getIdUsuario().toString())).andExpect(status().isForbidden());
        postJson("/notifications/register-token",staff,Map.of("token","device-"+UUID.randomUUID(),"id_empresa",other.getIdEmpresa())).andExpect(status().isNotFound());
        postJson("/notifications/send-alert",staff,Map.of("id_empresa",company.getIdEmpresa(),"titulo","Falso","mensaje","Falso")).andExpect(status().isForbidden());
    }
    @Test void analyticsDeniesRestrictedInputsBeforeContactingExternalService()throws Exception {
        permit("dashboard:read","sale:read","inventory:read","product:read","product:read:nombre");getJson("/api/reportes/"+company.getIdEmpresa()+"/analitica?id_sucursal="+a.getIdSucursal(),staff).andExpect(status().isForbidden());
    }
    @Test void dashboardSocketSendsNothingBeforeAuthenticationAndRejectsForeignCompany()throws Exception {
        var socket=socket("/ws/administrador/"+other.getIdEmpresa());dashboardSocket.afterConnectionEstablished(socket);org.mockito.Mockito.verify(socket,org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
        dashboardSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage(json.writeValueAsString(Map.of("tipo","autenticar","token",auth(staff).substring(7)))));
        org.mockito.Mockito.verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);org.mockito.Mockito.verify(socket,org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }
    @Test void dashboardSocketRechecksRoleOnEveryMessage()throws Exception {
        companyScope();permit("dashboard:read","sale:read","inventory:read","cash_register:read","product:read","product:read:nombre");var socket=socket("/ws/administrador/"+company.getIdEmpresa());dashboardSocket.afterConnectionEstablished(socket);
        dashboardSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage(json.writeValueAsString(Map.of("tipo","autenticar","token",auth(staff).substring(7)))));
        org.mockito.Mockito.verify(socket,org.mockito.Mockito.times(1)).sendMessage(org.mockito.ArgumentMatchers.any());permit("dashboard:read");
        dashboardSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage("{\"tipo\":\"dashboard\"}"));org.mockito.Mockito.verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);org.mockito.Mockito.verify(socket,org.mockito.Mockito.times(1)).sendMessage(org.mockito.ArgumentMatchers.any());
    }
    @Test void openIdleSocketClosesOnScheduledRevocationWithoutAnotherClientMessage()throws Exception {
        companyScope();permit("dashboard:read","sale:read","inventory:read","cash_register:read","product:read","product:read:nombre");
        var socket=socket("/ws/administrador/"+company.getIdEmpresa());dashboardSocket.afterConnectionEstablished(socket);
        dashboardSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage(json.writeValueAsString(Map.of("tipo","autenticar","token",auth(staff).substring(7)))));
        permit("dashboard:read");
        org.mockito.Mockito.verify(socket,org.mockito.Mockito.timeout(8000)).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        org.mockito.Mockito.verify(socket,org.mockito.Mockito.times(1)).sendMessage(org.mockito.ArgumentMatchers.any());
    }
    @Test void clientSocketCannotSubscribeToAnotherUserByChangingTheUrl()throws Exception {
        var socket=socket("/ws/clientes/"+customer.getIdUsuario());clientSocket.afterConnectionEstablished(socket);clientSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage(json.writeValueAsString(Map.of("tipo","autenticar","token",auth(staff).substring(7)))));org.mockito.Mockito.verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
    }
    @Test void invitationTokenCannotAuthenticateDashboardSocket()throws Exception {
        var socket=socket("/ws/administrador/"+company.getIdEmpresa());dashboardSocket.afterConnectionEstablished(socket);String token=jwt.createClientInvitationToken(company.getIdEmpresa(),owner.getIdUsuario(),role.getIdRol(),1,owner.getIdUsuario(),UUID.randomUUID().toString());
        dashboardSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage(json.writeValueAsString(Map.of("tipo","autenticar","token",token))));org.mockito.Mockito.verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
    }
    @Test void analyticsForwardsOnlyAuthorizedBranchAndNeverProductCost()throws Exception {
        permit("dashboard:read","sale:read","inventory:read","product:read","product:read:nombre","product:read:id_subcategoria");
        getJson("/api/reportes/"+company.getIdEmpresa()+"/analitica?id_sucursal="+a.getIdSucursal(),staff).andExpect(status().isOk());
        String sent=analyticsInput.get();assertThat(sent).doesNotContain("costo","purchase_price","profit");assertThat(json.readTree(sent).get("stock").size()).isEqualTo(1);
        getJson("/api/reportes/"+company.getIdEmpresa()+"/analitica?id_sucursal="+b.getIdSucursal(),staff).andExpect(status().isNotFound());
    }
    @Test void expiredSocketCredentialNeverReceivesData()throws Exception {
        var socket=socket("/ws/administrador/"+company.getIdEmpresa());dashboardSocket.afterConnectionEstablished(socket);
        String expired=jwt.createClientInvitationToken(company.getIdEmpresa(),owner.getIdUsuario(),role.getIdRol(),-1,owner.getIdUsuario(),UUID.randomUUID().toString());
        dashboardSocket.handleMessage(socket,new org.springframework.web.socket.TextMessage(json.writeValueAsString(Map.of("tipo","autenticar","token",expired))));
        org.mockito.Mockito.verify(socket).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);org.mockito.Mockito.verify(socket,org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }
    org.springframework.web.socket.WebSocketSession socket(String path){var socket=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);org.mockito.Mockito.when(socket.getUri()).thenReturn(java.net.URI.create("ws://localhost"+path));org.mockito.Mockito.when(socket.getAttributes()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());org.mockito.Mockito.when(socket.getId()).thenReturn(UUID.randomUUID().toString());org.mockito.Mockito.when(socket.isOpen()).thenReturn(true);opened.add(socket);return socket;}
    com.pos.notifications.domain.NotificacionHistorial notification(Integer recipient,Map<String,Object> data){var row=new com.pos.notifications.domain.NotificacionHistorial();row.setIdEmpresa(company.getIdEmpresa());row.setPrioridad(0);row.setTipo("ALERTA");row.setTitulo("COSTO_SECRETO");row.setMensaje("COSTO_SECRETO");row.setPayload(data);row.setLeido(false);row.setFecha(LocalDateTime.now());em.persist(row);return row;}
    Factura invoice(CajaSesion session){Venta sale=new Venta();sale.setTipoVenta(em.find(TipoVenta.class,saleType));sale.setIdCajaSesion(session.getIdCajaSesion());sale.setIdUsuario(staff.getIdUsuario());sale.setFecha(LocalDateTime.now());sale.setSubtotal(new BigDecimal("20.00"));sale.setDescuentoTotal(BigDecimal.ZERO);sale.setTotal(new BigDecimal("20.00"));sale.setEstado("PAGADA");em.persist(sale);Factura f=new Factura();f.setVenta(sale);f.setNitEmisor("123");f.setNumeroFactura(1);f.setFechaEmision(LocalDateTime.now());f.setNitCliente("0");f.setMontoTotal(new BigDecimal("20.00"));f.setIva(BigDecimal.ZERO);f.setCufd("FIXTURE");f.setCuf(UUID.randomUUID().toString());f.setXmlGenerado("<factura>segura</factura>");f.setPdfGenerado("/XMLS/fixture.pdf");em.persist(f);return f;}
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
