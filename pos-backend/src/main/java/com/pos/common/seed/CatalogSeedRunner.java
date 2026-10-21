package com.pos.common.seed;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pos.empresas.domain.Plan;
import com.pos.empresas.domain.PlanModulo;
import com.pos.empresas.domain.TipoMovimientoCaja;
import com.pos.empresas.repo.PlanModuloRepository;
import com.pos.empresas.repo.PlanRepository;
import com.pos.empresas.repo.TipoMovimientoCajaRepository;
import com.pos.inventario.domain.TipoMovimiento;
import com.pos.inventario.repo.TipoMovimientoRepository;
import com.pos.usuarios.domain.Modulo;
import com.pos.usuarios.domain.Permiso;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.RolPermiso;
import com.pos.usuarios.repo.ModuloRepository;
import com.pos.usuarios.repo.PermisoRepository;
import com.pos.usuarios.repo.RolPermisoRepository;
import com.pos.usuarios.repo.RolRepository;
import com.pos.ventas.domain.MetodoPago;
import com.pos.ventas.domain.TipoVenta;
import com.pos.ventas.repo.MetodoPagoRepository;
import com.pos.ventas.repo.TipoVentaRepository;

@Component
public class CatalogSeedRunner implements ApplicationRunner {

    private final RolRepository rolRepository;
    private final ModuloRepository moduloRepository;
    private final PermisoRepository permisoRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final TipoMovimientoCajaRepository tipoMovimientoCajaRepository;
    private final TipoMovimientoRepository tipoMovimientoRepository;
    private final TipoVentaRepository tipoVentaRepository;
    private final MetodoPagoRepository metodoPagoRepository;
    private final PlanRepository planRepository;
    private final PlanModuloRepository planModuloRepository;

    public CatalogSeedRunner(
            RolRepository rolRepository,
            ModuloRepository moduloRepository,
            PermisoRepository permisoRepository,
            RolPermisoRepository rolPermisoRepository,
            TipoMovimientoCajaRepository tipoMovimientoCajaRepository,
            TipoMovimientoRepository tipoMovimientoRepository,
            TipoVentaRepository tipoVentaRepository,
            MetodoPagoRepository metodoPagoRepository,
            PlanRepository planRepository,
            PlanModuloRepository planModuloRepository) {
        this.rolRepository = rolRepository;
        this.moduloRepository = moduloRepository;
        this.permisoRepository = permisoRepository;
        this.rolPermisoRepository = rolPermisoRepository;
        this.tipoMovimientoCajaRepository = tipoMovimientoCajaRepository;
        this.tipoMovimientoRepository = tipoMovimientoRepository;
        this.tipoVentaRepository = tipoVentaRepository;
        this.metodoPagoRepository = metodoPagoRepository;
        this.planRepository = planRepository;
        this.planModuloRepository = planModuloRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedModulos();
        seedPermisos();
        seedTiposMovimientoCaja();
        seedTiposMovimiento();
        seedTiposVenta();
        seedMetodosPago();
        seedPlanes();
    }

    private void seedRoles() {
        for (String nombre : List.of("ADMINISTRADOR", "CLIENTE", "EMPLEADO")) {
            if (rolRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
                Rol rol = new Rol();
                rol.setNombre(nombre);
                rol.setTipo("SISTEMA");
                rol.setDescripcion(nombre);
                rol.setActivo(true);
                rolRepository.save(rol);
            }
        }
    }

    private void seedModulos() {
        Map<String, String> modulos = Map.of(
                "USUARIOS", "Gestion de Usuarios",
                "EMPRESAS", "Gestion de Empresas",
                "INVENTARIO", "Gestion de Productos e Inventario",
                "VENTAS", "Gestion de Ventas",
                "CLIENTES", "Gestion de Clientes",
                "CAJAS", "Gestion de Cajas",
                "REPORTES", "Gestion de Reportes",
                "PEDIDOS", "Gestion de Pedidos Mayoristas",
                "AUDITORIA", "Auditoria y Bitacora");
        modulos.forEach((codigo, nombre) -> {
            if (moduloRepository.findByCodigoIgnoreCase(codigo).isEmpty()) {
                Modulo modulo = new Modulo();
                modulo.setCodigo(codigo);
                modulo.setNombre(nombre);
                moduloRepository.save(modulo);
            }
        });
    }

    private void seedPermisos() {
        record PermisoSeed(String codigo, String nombre, String moduloCodigo) {}
        List<PermisoSeed> permisos = List.of(
                new PermisoSeed("VENTA_VER", "Ver ventas", "VENTAS"),
                new PermisoSeed("VENTA_CREAR", "Crear ventas", "VENTAS"),
                new PermisoSeed("VENTA_ANULAR", "Anular ventas", "VENTAS"),
                new PermisoSeed("VENTA_DESCUENTO", "Aplicar descuentos", "VENTAS"),
                new PermisoSeed("FACTURA_EMITIR", "Emitir facturas", "VENTAS"),
                new PermisoSeed("FACTURA_REIMPRIMIR", "Reimprimir facturas", "VENTAS"),
                new PermisoSeed("USUARIO_VER", "Ver usuarios", "USUARIOS"),
                new PermisoSeed("USUARIO_CREAR", "Crear usuarios", "USUARIOS"),
                new PermisoSeed("USUARIO_EDITAR", "Editar usuarios", "USUARIOS"),
                new PermisoSeed("ROL_VER", "Ver roles", "USUARIOS"),
                new PermisoSeed("ROL_CREAR", "Crear roles", "USUARIOS"),
                new PermisoSeed("ROL_EDITAR", "Editar roles", "USUARIOS"),
                new PermisoSeed("REPORTE_GENERAR", "Generar reportes", "REPORTES"),
                new PermisoSeed("REPORTE_EXPORTAR", "Exportar reportes", "REPORTES"),
                new PermisoSeed("DASHBOARD_VER", "Ver dashboard", "REPORTES"),
                new PermisoSeed("PRODUCTO_VER", "Ver productos", "INVENTARIO"),
                new PermisoSeed("PRODUCTO_CREAR", "Crear productos", "INVENTARIO"),
                new PermisoSeed("PRODUCTO_EDITAR", "Editar productos", "INVENTARIO"),
                new PermisoSeed("STOCK_VER", "Ver stock", "INVENTARIO"),
                new PermisoSeed("STOCK_CONFIGURAR", "Configurar stock", "INVENTARIO"),
                new PermisoSeed("MOVIMIENTO_VER", "Ver movimientos", "INVENTARIO"),
                new PermisoSeed("MOVIMIENTO_REGISTRAR", "Registrar movimientos", "INVENTARIO"),
                new PermisoSeed("ALERTA_VER", "Ver alertas", "INVENTARIO"),
                new PermisoSeed("PEDIDO_VER", "Ver pedidos", "PEDIDOS"),
                new PermisoSeed("PEDIDO_GESTIONAR", "Gestionar pedidos", "PEDIDOS"),
                new PermisoSeed("CAJA_VER", "Ver cajas", "CAJAS"),
                new PermisoSeed("CAJA_EDITAR", "Editar cajas", "CAJAS"),
                new PermisoSeed("CAJA_CREAR", "Crear cajas", "CAJAS"),
                new PermisoSeed("CAJA_ABRIR", "Abrir cajas", "CAJAS"),
                new PermisoSeed("CAJA_CERRAR", "Cerrar cajas", "CAJAS"),
                new PermisoSeed("EMPRESA_EDITAR", "Editar empresa", "EMPRESAS"),
                new PermisoSeed("SUCURSAL_VER", "Ver sucursales", "EMPRESAS"),
                new PermisoSeed("SUCURSAL_CREAR", "Crear sucursales", "EMPRESAS"),
                new PermisoSeed("SUCURSAL_EDITAR", "Editar sucursales", "EMPRESAS"),
                new PermisoSeed("CLIENTE_VER", "Ver clientes", "CLIENTES"),
                new PermisoSeed("CLIENTE_CREAR", "Crear clientes", "CLIENTES"),
                new PermisoSeed("CLIENTE_EDITAR", "Editar clientes", "CLIENTES"),
                new PermisoSeed("CATEGORIA_VER", "Ver categorias de cliente", "CLIENTES"),
                new PermisoSeed("CATEGORIA_CREAR", "Crear categorias de cliente", "CLIENTES"),
                new PermisoSeed("CATEGORIA_EDITAR", "Editar categorias de cliente", "CLIENTES"),
                new PermisoSeed("BITACORA_VER", "Ver bitacora de auditoria", "AUDITORIA")
        );

        for (PermisoSeed ps : permisos) {
            if (!permisoRepository.existsByCodigo(ps.codigo())) {
                moduloRepository.findByCodigoIgnoreCase(ps.moduloCodigo()).ifPresent(modulo -> {
                    Permiso p = new Permiso();
                    p.setCodigo(ps.codigo());
                    p.setNombre(ps.nombre());
                    p.setModulo(modulo);
                    permisoRepository.save(p);
                });
            }
        }

        rolRepository.findByNombreIgnoreCase("ADMINISTRADOR").ifPresent(adminRol -> {
            List<Permiso> todosLosPermisos = permisoRepository.findAll();
            java.util.Set<Integer> permisosAsignados = rolPermisoRepository.findByRol_IdRol(adminRol.getIdRol()).stream()
                    .filter(rp -> rp.getPermiso() != null)
                    .map(rp -> rp.getPermiso().getIdPermiso())
                    .collect(java.util.stream.Collectors.toSet());
            for (Permiso p : todosLosPermisos) {
                if (!permisosAsignados.contains(p.getIdPermiso())) {
                    RolPermiso rp = new RolPermiso();
                    rp.setRol(adminRol);
                    rp.setPermiso(p);
                    rp.setActivo(true);
                    rolPermisoRepository.save(rp);
                }
            }
        });

        rolRepository.findByNombreIgnoreCase("EMPLEADO").ifPresent(empleadoRol -> {
            java.util.Set<String> codigosEmpleado = java.util.Set.of(
                    "CAJA_VER", "CAJA_ABRIR", "CAJA_CERRAR",
                    "VENTA_VER", "VENTA_CREAR", "VENTA_DESCUENTO",
                    "MOVIMIENTO_VER", "MOVIMIENTO_REGISTRAR",
                    "PRODUCTO_VER", "STOCK_VER",
                    "CLIENTE_VER", "CLIENTE_CREAR",
                    "SUCURSAL_VER"
            );
            java.util.Set<Integer> permisosAsignados = rolPermisoRepository.findByRol_IdRol(empleadoRol.getIdRol()).stream()
                    .filter(rp -> rp.getPermiso() != null)
                    .map(rp -> rp.getPermiso().getIdPermiso())
                    .collect(java.util.stream.Collectors.toSet());
            for (Permiso p : permisoRepository.findAll()) {
                if (codigosEmpleado.contains(p.getCodigo()) && !permisosAsignados.contains(p.getIdPermiso())) {
                    RolPermiso rp = new RolPermiso();
                    rp.setRol(empleadoRol);
                    rp.setPermiso(p);
                    rp.setActivo(true);
                    rolPermisoRepository.save(rp);
                }
            }
        });
    }

    private void seedTiposMovimientoCaja() {
        seedTipoCaja("APERTURA");
        seedTipoCaja("INGRESO");
        seedTipoCaja("EGRESO");
        seedTipoCaja("CIERRE");
        seedTipoCaja("AJUSTE_POSITIVO");
        seedTipoCaja("AJUSTE_NEGATIVO");
    }

    private void seedTipoCaja(String nombre) {
        if (tipoMovimientoCajaRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
            TipoMovimientoCaja tipo = new TipoMovimientoCaja();
            tipo.setNombre(nombre);
            tipo.setDescripcion(nombre);
            tipoMovimientoCajaRepository.save(tipo);
        }
    }

    private void seedTiposMovimiento() {
        seedTipoMovimiento("Entrada manual", "Incrementa stock por ingreso manual", "ENTRADA");
        seedTipoMovimiento("Salida manual", "Reduce stock por salida manual", "SALIDA");
        seedTipoMovimiento("Venta", "Reduce stock por venta", "SALIDA");
        seedTipoMovimiento("Ajuste positivo", "Corrige stock aumentando cantidad", "ENTRADA");
        seedTipoMovimiento("Ajuste negativo", "Corrige stock reduciendo cantidad", "SALIDA");
        seedTipoMovimiento("Merma", "Reduce stock por perdida o dano", "SALIDA");
    }

    private void seedTipoMovimiento(String nombre, String descripcion, String direccion) {
        if (tipoMovimientoRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
            TipoMovimiento tipo = new TipoMovimiento();
            tipo.setNombre(nombre);
            tipo.setDescripcion(descripcion);
            tipo.setDireccion(direccion);
            tipoMovimientoRepository.save(tipo);
        }
    }

    private void seedTiposVenta() {
        seedTipoVenta("Contado", "Venta al contado");
        seedTipoVenta("Credito", "Venta al Credito");
    }

    private void seedTipoVenta(String nombre, String descripcion) {
        if (tipoVentaRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
            TipoVenta tipo = new TipoVenta();
            tipo.setNombre(nombre);
            tipo.setDescripcion(descripcion);
            tipoVentaRepository.save(tipo);
        }
    }

    private void seedMetodosPago() {
        seedMetodoPago("EFECTIVO", "Pago en efectivo");
        seedMetodoPago("QR", "Pago por QR");
        seedMetodoPago("TARJETA", "Pago con tarjeta");
    }

    private void seedMetodoPago(String nombre, String descripcion) {
        if (metodoPagoRepository.findByNombreIgnoreCase(nombre).isEmpty()) {
            MetodoPago metodo = new MetodoPago();
            metodo.setNombre(nombre);
            metodo.setDescripcion(descripcion);
            metodoPagoRepository.save(metodo);
        }
    }

    private void seedPlanes() {
        seedPlan("Básico", "Plan ideal para emprendedores y pequeños negocios.", new BigDecimal("29.99"),
                List.of("USUARIOS", "INVENTARIO", "VENTAS", "CAJAS"));
        seedPlan("Pro", "Plan diseñado para medianas empresas con mayores necesidades.", new BigDecimal("59.99"),
                List.of("USUARIOS", "EMPRESAS", "INVENTARIO", "VENTAS", "CLIENTES", "CAJAS"));
        seedPlan("Premium", "Plan completo con todas las características del sistema.", new BigDecimal("99.99"),
                List.of("USUARIOS", "EMPRESAS", "INVENTARIO", "VENTAS", "CLIENTES", "CAJAS", "REPORTES", "PEDIDOS"));
    }

    private void seedPlan(String nombre, String descripcion, BigDecimal precio, List<String> modulos) {
        Plan plan = planRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Plan nuevo = new Plan();
            nuevo.setNombre(nombre);
            nuevo.setDescripcion(descripcion);
            nuevo.setPrecio(precio);
            return planRepository.save(nuevo);
        });
        for (String codigo : modulos) {
            moduloRepository.findByCodigoIgnoreCase(codigo).ifPresent(modulo -> {
                boolean exists = planModuloRepository.findByPlan_IdPlanAndIdModulo(plan.getIdPlan(), modulo.getIdModulo()).isPresent();
                if (!exists) {
                    PlanModulo relacion = new PlanModulo();
                    relacion.setPlan(plan);
                    relacion.setIdModulo(modulo.getIdModulo());
                    planModuloRepository.save(relacion);
                }
            });
        }
    }
}
