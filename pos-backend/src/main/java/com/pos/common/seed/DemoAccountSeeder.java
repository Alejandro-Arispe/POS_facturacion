package com.pos.common.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.clientes.domain.CategoriaCliente;
import com.pos.clientes.domain.Cliente;
import com.pos.common.security.PasswordService;
import com.pos.empresas.domain.Caja;
import com.pos.empresas.domain.CajaCierreDetalle;
import com.pos.empresas.domain.CajaSesion;
import com.pos.empresas.domain.ConfiguracionSistema;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.MovimientoCaja;
import com.pos.empresas.domain.Plan;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.domain.TipoMovimientoCaja;
import com.pos.inventario.domain.MovimientoInventario;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.domain.TipoMovimiento;
import com.pos.productos.domain.CategoriaProducto;
import com.pos.productos.domain.Producto;
import com.pos.productos.domain.SubcategoriaProducto;
import com.pos.common.storage.ProductImageStorage;
import com.pos.siat.service.SiatFacturaService;
import com.pos.usuarios.domain.Persona;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.MetodoPago;
import com.pos.ventas.domain.TipoVenta;
import com.pos.ventas.domain.Venta;
import com.pos.ventas.domain.VentaPago;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Dataset atomico e idempotente en una empresa exclusiva para la cuenta indicada. */
@Service
@Profile("seed-demo")
public class DemoAccountSeeder {

    private static final ZoneId ZONE = ZoneId.of("America/La_Paz");
    private static final int DAYS = 30;
    private final EntityManager em;
    private final PasswordService passwords;
    private final SiatFacturaService siat;
    private final ProductImageStorage images;

    /** Ilustraciones del catalogo de ejemplo, en el mismo orden que los nombres de producto de {@link #products}. */
    private static final String[] PRODUCT_IMAGES = {"arroz", "azucar", "aceite", "fideo", "agua", "cola", "jugo",
            "cafe", "detergente", "lavavajillas", "jabon", "papel"};

    public DemoAccountSeeder(EntityManager em, PasswordService passwords, SiatFacturaService siat,
            ProductImageStorage images) {
        this.em = em;
        this.passwords = passwords;
        this.siat = siat;
        this.images = images;
    }

    /** Copia al almacenamiento de imagenes la ilustracion del producto; si falta el recurso el producto queda sin imagen. */
    private String productImage(int position) {
        if (position >= PRODUCT_IMAGES.length) return null;
        try (var stream = DemoAccountSeeder.class.getResourceAsStream("/seed/productos/" + PRODUCT_IMAGES[position] + ".png")) {
            return stream == null ? null : images.save(stream.readAllBytes(), "png");
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    /** Crea una cuenta activa con contrasena conocida; si el correo ya existe la devuelve sin modificarla. */
    @Transactional
    public Usuario ensureUser(String email, String password, String fullName, String document) {
        Usuario existing = em.createQuery("select u from Usuario u where lower(u.email) = lower(:email)", Usuario.class)
                .setParameter("email", email.trim()).getResultStream().findFirst().orElse(null);
        if (existing != null) return existing;
        Persona person = new Persona();
        person.setNombreCompleto(fullName);
        person.setFechaNacimiento(LocalDate.of(1990, 1, 15));
        person.setGenero("N/E");
        person.setTelefono("70000000");
        person.setDocumento(document);
        save(person);
        Usuario user = new Usuario();
        user.setPersona(person);
        user.setEmail(email.trim());
        user.setContrasena(passwords.hash(password));
        user.setFechaCreacion(LocalDate.now(ZONE));
        user.setActivo(true);
        return save(user);
    }

    public record Login(String role, String email, String password, String scope) { }

    /**
     * Equipo de la empresa: dos cajeros (uno por sucursal, rol EMPLEADO) y un cliente con acceso.
     * Es idempotente: si el correo ya existe no se vuelve a crear ni se cambia su contrasena.
     */
    @Transactional
    public List<Login> seedTeam(Integer companyId, String password) {
        Empresa company = em.find(Empresa.class, companyId);
        List<Sucursal> branches = em.createQuery("select s from Sucursal s where s.empresa = :c order by s.idSucursal",
                Sucursal.class).setParameter("c", company).getResultList();
        LocalDate today = LocalDate.now(ZONE);
        List<Login> logins = new ArrayList<>();
        String[] cashiers = {"cajero.centro@doncarlos.com.bo", "cajero.sur@doncarlos.com.bo"};
        String[] cashierNames = {"Rosa Quispe Mamani", "Marco Condori Flores"};
        for (int i = 0; i < Math.min(cashiers.length, branches.size()); i++) {
            Usuario user = ensureUser(cashiers[i], password, cashierNames[i], "512345" + (i + 6));
            boolean linked = !em.createQuery("select ur from UsuarioRol ur where ur.usuario = :u and ur.idEmpresa = :c",
                    UsuarioRol.class).setParameter("u", user).setParameter("c", companyId).getResultList().isEmpty();
            if (!linked) {
                UsuarioRol link = new UsuarioRol();
                link.setUsuario(user);
                link.setRol(catalog(Rol.class, "EMPLEADO"));
                link.setIdEmpresa(companyId);
                link.setIdSucursal(branches.get(i).getIdSucursal());
                link.setFecha(today);
                save(link);
            }
            logins.add(new Login("Empleado (cajero)", cashiers[i], password, branches.get(i).getNombre()));
        }
        String clientEmail = "mariela.gutierrez@gmail.com";
        Usuario clientUser = ensureUser(clientEmail, password, "Mariela Gutierrez Paz", "6012345");
        boolean hasClient = !em.createQuery("select c from Cliente c where c.idUsuario = :u and c.idEmpresa = :e",
                Cliente.class).setParameter("u", clientUser.getIdUsuario()).setParameter("e", companyId).getResultList().isEmpty();
        if (!hasClient) {
            link(clientUser, catalog(Rol.class, "CLIENTE"), company, today);
            CategoriaCliente category = em.createQuery("select c from CategoriaCliente c where c.idEmpresa = :e and c.nombre = 'Mayorista'",
                    CategoriaCliente.class).setParameter("e", companyId).getSingleResult();
            Cliente client = new Cliente();
            client.setIdUsuario(clientUser.getIdUsuario());
            client.setIdEmpresa(companyId);
            client.setCategoriaCliente(category);
            client.setCodigoCliente("CLI-0100");
            client.setLimiteCredito(category.getLimiteCredito());
            save(client);
        }
        logins.add(new Login("Cliente", clientEmail, password, "Pedidos y credito"));
        return logins;
    }

    public record Result(Integer companyId, String companyName, boolean created) { }

    @Transactional
    public Result seed(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Indica el correo de una cuenta existente.");
        }
        // Serializa dos ejecuciones simultaneas para una misma cuenta.
        Usuario owner = em.createQuery("select u from Usuario u where lower(u.email) = lower(:email)", Usuario.class)
                .setParameter("email", email.trim()).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No existe la cuenta " + email + ". Registrala primero."));
        if (!Boolean.TRUE.equals(owner.getActivo())) {
            throw new IllegalStateException("La cuenta debe estar activa antes de cargar datos de prueba.");
        }
        String nit = String.format("1023456%03d", owner.getIdUsuario());
        Empresa existing = em.createQuery("select e from Empresa e where e.nit = :nit", Empresa.class)
                .setParameter("nit", nit).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            Long links = em.createQuery("select count(ur) from UsuarioRol ur where ur.usuario = :owner "
                    + "and ur.idEmpresa = :company and ur.rol.nombre = 'ADMINISTRADOR' and ur.activo = true", Long.class)
                    .setParameter("owner", owner).setParameter("company", existing.getIdEmpresa()).getSingleResult();
            if (links == 0) {
                throw new IllegalStateException("El NIT de prueba ya existe sin la relacion esperada con la cuenta.");
            }
            return new Result(existing.getIdEmpresa(), existing.getNombre(), false);
        }

        LocalDate today = LocalDate.now(ZONE);
        Empresa company = new Empresa();
        company.setNombre("Minimarket Don Carlos");
        company.setRazonSocial("Comercial Don Carlos S.R.L.");
        company.setNit(nit);
        company.setCorreo(owner.getIdUsuario() == 1 ? "contacto@doncarlos.com.bo" : "contacto" + owner.getIdUsuario() + "@doncarlos.com.bo");
        company.setFechaCreacion(today.minusDays(DAYS + 1));
        save(company);
        link(owner, catalog(Rol.class, "ADMINISTRADOR"), company, today);

        HistorialSuscripcion subscription = new HistorialSuscripcion();
        subscription.setEmpresa(company);
        subscription.setPlan(catalog(Plan.class, "Premium"));
        subscription.setFechaInicio(today.minusDays(DAYS + 1));
        subscription.setFechaFin(today.plusYears(1));
        subscription.setEstado("Activa");
        save(subscription);
        ConfiguracionSistema settings = new ConfiguracionSistema();
        settings.setEmpresa(company);
        settings.setActivarNotificacionesPush(false);
        save(settings);

        List<Producto> products = products(company);
        List<Cliente> clients = clients(company, today);
        for (int branch = 0; branch < 2; branch++) {
            Sucursal location = new Sucursal();
            location.setEmpresa(company);
            location.setNombre(branch == 0 ? "Sucursal Centro" : "Sucursal Zona Sur");
            location.setDireccion(branch == 0 ? "Av. Camacho 1245, Zona Central" : "Calle 21 de Calacoto 8340, Zona Sur");
            location.setCiudad("La Paz");
            location.setTelefono(branch == 0 ? "22201234" : "22795678");
            location.setFechaRegistro(company.getFechaCreacion());
            save(location);
            Caja register = new Caja();
            register.setSucursal(location);
            register.setNombre("Caja principal");
            register.setCodigo("CAJA-" + company.getIdEmpresa() + "0" + (branch + 1));
            register.setFechaCreacion(company.getFechaCreacion());
            save(register);
            history(owner, register, products, clients, today, branch);
        }
        em.flush();
        return new Result(company.getIdEmpresa(), company.getNombre(), true);
    }

    private List<Producto> products(Empresa company) {
        String[][] names = {
                {"Arroz Grano de Oro 1 kg", "Azucar Guabira 1 kg", "Aceite Fino 1 L", "Fideo Lazzaroni 400 g"},
                {"Agua Vital 2 L", "Coca-Cola 2 L", "Jugo Del Valle Naranja 1 L", "Cafe Nescafe Clasico 100 g"},
                {"Detergente Ola 1 kg", "Lavavajillas Axion 750 ml", "Jabon de tocador Lux", "Papel higienico Elite 4 rollos"}
        };
        String[][] descriptions = {
                {"Arroz grano largo de primera calidad", "Azucar blanca refinada", "Aceite vegetal comestible", "Fideo tipo tallarin"},
                {"Agua purificada sin gas", "Bebida gaseosa sabor cola", "Jugo de naranja listo para tomar", "Cafe soluble instantaneo"},
                {"Detergente en polvo para ropa", "Lavavajillas en crema para vajilla", "Jabon de tocador con aroma", "Papel higienico doble hoja"}
        };
        String[][] prices = {{"9.50", "7.00", "16.50", "6.00"}, {"6.00", "12.00", "10.50", "22.00"},
                {"19.00", "14.50", "5.00", "18.00"}};
        String[] groups = {"Abarrotes", "Bebidas", "Limpieza"};
        String[] subgroups = {"Abarrotes basicos", "Bebidas y jugos", "Limpieza del hogar"};
        List<Producto> result = new ArrayList<>();
        for (int group = 0; group < names.length; group++) {
            CategoriaProducto category = new CategoriaProducto();
            category.setIdEmpresa(company.getIdEmpresa());
            // El esquema actual exige nombres de categoria unicos globalmente: solo se desambigua si ya existe.
            boolean taken = !em.createQuery("select c.idCategoriaProducto from CategoriaProducto c where c.nombre = :n", Integer.class)
                    .setParameter("n", groups[group]).getResultList().isEmpty();
            category.setNombre(taken ? groups[group] + " - " + company.getNombre() + " " + company.getIdEmpresa() : groups[group]);
            category.setDescripcion("Productos de consumo diario: " + groups[group].toLowerCase());
            save(category);
            SubcategoriaProducto subcategory = new SubcategoriaProducto();
            subcategory.setCategoriaProducto(category);
            subcategory.setNombre(subgroups[group]);
            save(subcategory);
            for (int index = 0; index < names[group].length; index++) {
                Producto product = new Producto();
                product.setIdEmpresa(company.getIdEmpresa());
                product.setSubcategoria(subcategory);
                product.setNombre(names[group][index]);
                product.setCodigoBarra(String.format("770%03d%07d", company.getIdEmpresa(), result.size() + 1));
                product.setDescripcion(descriptions[group][index]);
                product.setImagen(productImage(result.size()));
                product.setUnidadMedida(names[group][index].contains(" kg") ? "Kilogramo" : names[group][index].contains(" L") ? "Litro" : "Unidad");
                // Homologacion SIAT simulada (codigos del catalogo SIN sembrado al arrancar).
                String productName = names[group][index];
                product.setCodigoActividad(group == 2 ? "47190" : "47110");
                product.setCodigoProductoSin(group == 0 ? 99100 : group == 1 ? 24410 : 62111);
                product.setCodigoUnidadMedidaSin(productName.contains(" kg") ? 22 : productName.contains(" L") ? 47 : 58);
                product.setPrecio(new BigDecimal(prices[group][index]));
                product.setCosto(product.getPrecio().multiply(new BigDecimal("0.65")).setScale(2, RoundingMode.HALF_UP));
                result.add(save(product));
            }
        }
        return result;
    }

    private List<Cliente> clients(Empresa company, LocalDate today) {
        List<CategoriaCliente> categories = new ArrayList<>();
        for (String name : List.of("Minorista", "Mayorista")) {
            CategoriaCliente category = new CategoriaCliente();
            category.setIdEmpresa(company.getIdEmpresa());
            category.setNombre(name);
            category.setDescripcion(name.equals("Mayorista") ? "Compras al por mayor con descuento y credito" : "Clientes de venta al detalle");
            category.setDescuentoBase(new BigDecimal(name.equals("Mayorista") ? "5.00" : "0.00"));
            category.setPlazoCredito(name.equals("Mayorista") ? 30 : 0);
            category.setLimiteCredito(new BigDecimal(name.equals("Mayorista") ? "3000.00" : "0.00"));
            categories.add(save(category));
        }
        String[] names = {"Ana Perez Soliz", "Luis Mamani Choque", "Carla Rojas Vega", "Jose Flores Aliaga", "Maria Quispe Huanca",
                "Pedro Vargas Lima", "Lucia Torres Cruz", "Diego Molina Paredes"};
        String[] emails = {"ana.perez", "luis.mamani", "carla.rojas", "jose.flores", "maria.quispe",
                "pedro.vargas", "lucia.torres", "diego.molina"};
        String hash = passwords.hash(UUID.randomUUID().toString());
        List<Cliente> result = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Persona person = new Persona();
            person.setNombreCompleto(names[i]);
            person.setFechaNacimiento(LocalDate.of(1990 + i, 1 + i, 10));
            person.setGenero("N/E");
            person.setTelefono("7" + (1000000 + i * 113457));
            person.setDocumento(String.valueOf(5200000 + company.getIdEmpresa() * 100 + i));
            save(person);
            Usuario user = new Usuario();
            user.setPersona(person);
            user.setEmail(emails[i] + (company.getIdEmpresa() == 1 ? "" : company.getIdEmpresa()) + "@gmail.com");
            user.setContrasena(hash);
            user.setFechaCreacion(company.getFechaCreacion());
            user.setActivo(true);
            save(user);
            link(user, catalog(Rol.class, "CLIENTE"), company, today);
            Cliente client = new Cliente();
            client.setIdUsuario(user.getIdUsuario());
            client.setCategoriaCliente(categories.get(i % categories.size()));
            client.setCodigoCliente(String.format("CLI-%04d", i + 1));
            client.setLimiteCredito(client.getCategoriaCliente().getLimiteCredito());
            result.add(save(client));
        }
        return result;
    }

    private void history(Usuario owner, Caja register, List<Producto> products, List<Cliente> clients,
            LocalDate today, int branch) {
        TipoVenta cashSale = catalog(TipoVenta.class, "Contado");
        TipoMovimiento entry = catalog(TipoMovimiento.class, "Entrada manual");
        TipoMovimiento exit = catalog(TipoMovimiento.class, "Venta");
        TipoMovimientoCaja opening = catalog(TipoMovimientoCaja.class, "APERTURA");
        TipoMovimientoCaja income = catalog(TipoMovimientoCaja.class, "INGRESO");
        TipoMovimientoCaja closing = catalog(TipoMovimientoCaja.class, "CIERRE");
        List<MetodoPago> methods = List.of(catalog(MetodoPago.class, "EFECTIVO"),
                catalog(MetodoPago.class, "QR"), catalog(MetodoPago.class, "TARJETA"));
        Random random = new Random(2026L + branch);
        Map<Integer, Integer> sold = new LinkedHashMap<>();
        for (int day = DAYS; day >= 1; day--) {
            LocalDate date = today.minusDays(day);
            CajaSesion session = new CajaSesion();
            session.setCaja(register);
            session.setIdUsuario(owner.getIdUsuario());
            session.setFechaApertura(utc(date, 8));
            session.setFechaCierre(utc(date, 20));
            session.setMontoInicial(new BigDecimal("200.00"));
            session.setEstado("Cerrado");
            session.setNota("Turno regular");
            save(session);
            Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
            balances.put(methods.get(0).getIdMetodoPago(), session.getMontoInicial());
            cashMovement(session, opening, methods.get(0).getIdMetodoPago(), session.getMontoInicial(),
                    session.getFechaApertura(), "Apertura de caja");
            for (int saleIndex = 0; saleIndex < 2; saleIndex++) {
                LocalDateTime dateTime = utc(date, 10 + saleIndex * 6);
                Cliente client = clients.get(random.nextInt(clients.size()));
                Venta sale = new Venta();
                sale.setTipoVenta(cashSale);
                sale.setIdCliente(client.getIdCliente());
                sale.setIdCajaSesion(session.getIdCajaSesion());
                sale.setIdUsuario(owner.getIdUsuario());
                sale.setFecha(dateTime);
                sale.setEstado("PAGADA");
                save(sale);
                int first = random.nextInt(products.size());
                for (int line = 0; line < 2; line++) {
                    Producto product = products.get((first + line) % products.size());
                    int quantity = 1 + random.nextInt(4);
                    BigDecimal gross = product.getPrecio().multiply(BigDecimal.valueOf(quantity));
                    BigDecimal discount = gross.multiply(client.getCategoriaCliente().getDescuentoBase())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    DetalleVenta detail = new DetalleVenta();
                    detail.setVenta(sale);
                    detail.setIdProducto(product.getIdProducto());
                    detail.setDescripcion(product.getNombre());
                    detail.setCantidad(quantity);
                    detail.setCodigoProductoSin(product.getCodigoProductoSin());
                    detail.setCodigoUnidadMedidaSin(product.getCodigoUnidadMedidaSin());
                    detail.setPrecioUnitario(product.getPrecio());
                    detail.setDescuento(discount);
                    detail.setSubtotal(gross.subtract(discount));
                    detail.setTotal(detail.getSubtotal());
                    save(detail);
                    sale.setSubtotal(sale.getSubtotal().add(gross));
                    sale.setDescuentoTotal(sale.getDescuentoTotal().add(discount));
                    sold.merge(product.getIdProducto(), quantity, Integer::sum);
                    inventoryMovement(owner, register.getSucursal(), product, exit, quantity, dateTime,
                            "Venta " + sale.getIdVenta());
                }
                sale.setTotal(sale.getSubtotal().subtract(sale.getDescuentoTotal()));
                MetodoPago method = methods.get(random.nextInt(methods.size()));
                VentaPago payment = new VentaPago();
                payment.setVenta(sale);
                payment.setIdVenta(sale.getIdVenta());
                payment.setIdMetodoPago(method.getIdMetodoPago());
                payment.setMonto(sale.getTotal());
                payment.setFecha(dateTime);
                save(payment);
                cashMovement(session, income, method.getIdMetodoPago(), sale.getTotal(), dateTime,
                        "Venta " + sale.getIdVenta());
                balances.merge(method.getIdMetodoPago(), sale.getTotal(), BigDecimal::add);
                // Una de cada tres ventas lleva factura SIAT simulada (CUF, CUFD y QR).
                if ((day + saleIndex) % 3 == 0) {
                    siat.emitir(sale, session, client, new SiatFacturaService.Comprador(1, null, null), method.getNombre());
                }
            }
            session.setMontoFinal(balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            balances.forEach((method, amount) -> {
                CajaCierreDetalle detail = new CajaCierreDetalle();
                detail.setCajaSesion(session);
                detail.setIdMetodoPago(method);
                detail.setMontoEsperado(amount);
                detail.setMontoReal(amount);
                detail.setObservacion("Sin diferencias");
                save(detail);
                cashMovement(session, closing, method, amount, session.getFechaCierre(), "Cierre de caja");
            });
        }
        for (int i = 0; i < products.size(); i++) {
            Producto product = products.get(i);
            int remaining = i == 0 ? 0 : i == 1 ? 4 : 45 + i * 7 + branch * 5;
            Stock stock = new Stock();
            stock.setProducto(product);
            stock.setIdSucursal(register.getSucursal().getIdSucursal());
            stock.setCantidad(remaining);
            stock.setStockMinimo(10);
            stock.setStockMaximo(250);
            stock.setFechaActualizacion(utc(today, 0));
            save(stock);
            inventoryMovement(owner, register.getSucursal(), product, entry,
                    remaining + sold.getOrDefault(product.getIdProducto(), 0), utc(today.minusDays(DAYS + 1), 7),
                    "Inventario inicial");
        }
    }

    private void link(Usuario user, Rol role, Empresa company, LocalDate date) {
        UsuarioRol link = new UsuarioRol();
        link.setUsuario(user);
        link.setRol(role);
        link.setIdEmpresa(company.getIdEmpresa());
        link.setFecha(date);
        save(link);
    }

    private void inventoryMovement(Usuario owner, Sucursal branch, Producto product, TipoMovimiento type,
            int quantity, LocalDateTime date, String note) {
        MovimientoInventario movement = new MovimientoInventario();
        movement.setProducto(product);
        movement.setTipoMovimiento(type);
        movement.setIdUsuario(owner.getIdUsuario());
        movement.setIdSucursal(branch.getIdSucursal());
        movement.setCantidad(quantity);
        movement.setFechaMovimiento(date);
        movement.setObservacion(note);
        save(movement);
    }

    private void cashMovement(CajaSesion session, TipoMovimientoCaja type, Integer method, BigDecimal amount,
            LocalDateTime date, String concept) {
        MovimientoCaja movement = new MovimientoCaja();
        movement.setCajaSesion(session);
        movement.setTipoMovimientoCaja(type);
        movement.setIdMetodoPago(method);
        movement.setIdUsuario(session.getIdUsuario());
        movement.setMonto(amount);
        movement.setFecha(date);
        movement.setConcepto(concept);
        save(movement);
    }

    private <T> T catalog(Class<T> type, String name) {
        return em.createQuery("select c from " + type.getSimpleName() + " c where lower(c.nombre) = lower(:name)", type)
                .setParameter("name", name).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta el catalogo " + type.getSimpleName() + ": " + name));
    }

    private <T> T save(T entity) {
        em.persist(entity);
        return entity;
    }

    private static LocalDateTime utc(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
