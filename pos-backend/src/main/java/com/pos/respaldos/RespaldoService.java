package com.pos.respaldos;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.hibernate.Session;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.ConfiguracionSistema;
import com.pos.empresas.domain.Empresa;
import com.pos.empresas.domain.HistorialSuscripcion;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.ConfiguracionSistemaRepository;
import com.pos.empresas.repo.EmpresaRepository;
import com.pos.empresas.repo.HistorialSuscripcionRepository;
import com.pos.empresas.repo.PlanRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.productos.domain.CategoriaProducto;
import com.pos.productos.domain.Producto;
import com.pos.productos.domain.SubcategoriaProducto;
import com.pos.productos.repo.CategoriaProductoRepository;
import com.pos.productos.repo.ProductoRepository;
import com.pos.productos.repo.SubcategoriaProductoRepository;
import com.pos.respaldos.RespaldoDocumento.CategoriaDatos;
import com.pos.respaldos.RespaldoDocumento.ConfiguracionDatos;
import com.pos.respaldos.RespaldoDocumento.EmpresaDatos;
import com.pos.respaldos.RespaldoDocumento.HistorialDatos;
import com.pos.respaldos.RespaldoDocumento.ProductoDatos;
import com.pos.respaldos.RespaldoDocumento.RolDatos;
import com.pos.respaldos.RespaldoDocumento.RolPermisoDatos;
import com.pos.respaldos.RespaldoDocumento.SubcategoriaDatos;
import com.pos.respaldos.RespaldoDocumento.SucursalDatos;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.RolPermiso;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.PermisoRepository;
import com.pos.usuarios.repo.RolPermisoRepository;
import com.pos.usuarios.repo.RolRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RespaldoService {

    private static final Set<String> IDENTIDAD = Set.of(
            "categoria_producto.id_categoria_producto",
            "subcategoria_producto.id_subcategoria",
            "producto.id_producto",
            "sucursal.id_sucursal",
            "rol.id_rol",
            "rol_permiso.id_rol_permiso",
            "configuracion_sistema.id_configuracion",
            "historial_suscripcion.id_historial_suscripcion");

    private final EmpresaAccess empresaAccess;
    private final EmpresaRepository empresaRepository;
    private final SucursalRepository sucursalRepository;
    private final CategoriaProductoRepository categoriaProductoRepository;
    private final SubcategoriaProductoRepository subcategoriaProductoRepository;
    private final ProductoRepository productoRepository;
    private final RolRepository rolRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final PermisoRepository permisoRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final HistorialSuscripcionRepository historialSuscripcionRepository;
    private final PlanRepository planRepository;
    private final RespaldoAlmacen almacen;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public RespaldoService(
            EmpresaAccess empresaAccess,
            EmpresaRepository empresaRepository,
            SucursalRepository sucursalRepository,
            CategoriaProductoRepository categoriaProductoRepository,
            SubcategoriaProductoRepository subcategoriaProductoRepository,
            ProductoRepository productoRepository,
            RolRepository rolRepository,
            RolPermisoRepository rolPermisoRepository,
            PermisoRepository permisoRepository,
            ConfiguracionSistemaRepository configuracionSistemaRepository,
            HistorialSuscripcionRepository historialSuscripcionRepository,
            PlanRepository planRepository,
            RespaldoAlmacen almacen,
            ObjectMapper objectMapper,
            EntityManager entityManager) {
        this.empresaAccess = empresaAccess;
        this.empresaRepository = empresaRepository;
        this.sucursalRepository = sucursalRepository;
        this.categoriaProductoRepository = categoriaProductoRepository;
        this.subcategoriaProductoRepository = subcategoriaProductoRepository;
        this.productoRepository = productoRepository;
        this.rolRepository = rolRepository;
        this.rolPermisoRepository = rolPermisoRepository;
        this.permisoRepository = permisoRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.historialSuscripcionRepository = historialSuscripcionRepository;
        this.planRepository = planRepository;
        this.almacen = almacen;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public RespaldoRespuestas.Generado crear(Integer idEmpresa, Usuario usuario) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return guardar(idEmpresa);
    }

    @Transactional(readOnly = true)
    public void exportarProgramado(Integer idEmpresa) {
        if (!empresaRepository.existsById(idEmpresa)) {
            return;
        }
        guardar(idEmpresa);
    }

    public List<RespaldoRespuestas.Resumen> listar(Integer idEmpresa, Usuario usuario) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return almacen.listar(idEmpresa);
    }

    public byte[] descargar(Integer idEmpresa, Usuario usuario, String archivo) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return almacen.leer(idEmpresa, archivo);
    }

    @Transactional
    public RespaldoRespuestas.Restaurado restaurar(Integer idEmpresa, Usuario usuario, String archivo) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        RespaldoDocumento documento = parsear(almacen.leer(idEmpresa, archivo));
        if (documento.idEmpresa() == null || !idEmpresa.equals(documento.idEmpresa())) {
            throw ApiException.badRequest("El id_empresa del respaldo no coincide con la empresa de la URL.");
        }
        try {
            aplicar(idEmpresa, documento);
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            String causa = ex.getMostSpecificCause() == null ? null : ex.getMostSpecificCause().getMessage();
            if (causa == null || causa.isBlank()) {
                causa = "conflicto con datos existentes";
            }
            throw ApiException.badRequest("No se pudo restaurar la empresa. " + causa);
        }
        return new RespaldoRespuestas.Restaurado(
                archivo,
                idEmpresa,
                RespaldoDocumento.TABLAS,
                RespaldoDocumento.INCLUYE_VENTAS,
                RespaldoDocumento.MENSAJE_VENTAS);
    }

    private RespaldoRespuestas.Generado guardar(Integer idEmpresa) {
        LocalDateTime fecha = LocalDateTime.now().withNano(0);
        RespaldoDocumento documento = armar(idEmpresa, fecha);
        try {
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(documento);
            String archivo = almacen.guardar(idEmpresa, json, fecha);
            return new RespaldoRespuestas.Generado(
                    archivo,
                    fecha,
                    idEmpresa,
                    RespaldoDocumento.TABLAS,
                    RespaldoDocumento.INCLUYE_VENTAS,
                    RespaldoDocumento.MENSAJE_VENTAS);
        } catch (JacksonException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo serializar el respaldo.");
        }
    }

    private RespaldoDocumento armar(Integer idEmpresa, LocalDateTime fecha) {
        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
        List<SucursalDatos> sucursales = sucursalRepository.findByEmpresa_IdEmpresa(idEmpresa).stream()
                .map(this::sucursalDatos)
                .toList();
        List<CategoriaDatos> categorias = categoriaProductoRepository.findByIdEmpresa(idEmpresa).stream()
                .map(this::categoriaDatos)
                .toList();
        List<SubcategoriaDatos> subcategorias = subcategoriaProductoRepository.findByIdEmpresa(idEmpresa).stream()
                .map(this::subcategoriaDatos)
                .toList();
        List<ProductoDatos> productos = productoRepository.findByIdEmpresa(idEmpresa).stream()
                .map(this::productoDatos)
                .toList();
        List<Rol> roles = rolesCustom(idEmpresa);
        List<RolDatos> rolesDatos = roles.stream().map(this::rolDatos).toList();
        List<RolPermisoDatos> permisos = new ArrayList<>();
        for (Rol rol : roles) {
            for (RolPermiso enlace : rolPermisoRepository.findByRol_IdRol(rol.getIdRol())) {
                if (enlace.getPermiso() == null) {
                    continue;
                }
                permisos.add(new RolPermisoDatos(
                        enlace.getIdRolPermiso(),
                        rol.getIdRol(),
                        enlace.getPermiso().getIdPermiso(),
                        enlace.getActivo()));
            }
        }
        ConfiguracionDatos configuracion = configuracionSistemaRepository.findByEmpresa_IdEmpresa(idEmpresa)
                .map(this::configuracionDatos)
                .orElse(null);
        List<HistorialDatos> historial = historialSuscripcionRepository.findByEmpresa_IdEmpresa(idEmpresa).stream()
                .map(this::historialDatos)
                .toList();
        return new RespaldoDocumento(
                idEmpresa,
                fecha,
                RespaldoDocumento.INCLUYE_VENTAS,
                RespaldoDocumento.MENSAJE_VENTAS,
                RespaldoDocumento.TABLAS,
                empresaDatos(empresa),
                sucursales,
                categorias,
                subcategorias,
                productos,
                rolesDatos,
                permisos,
                configuracion,
                historial);
    }

    /**
     * Restaura solo filas de esta empresa. Los ids son identity: si la fila ya existe se actualiza;
     * si no, se inserta con el id del archivo. Las ventas no se tocan.
     */
    private void aplicar(Integer idEmpresa, RespaldoDocumento documento) {
        List<SucursalDatos> sucursales = lista(documento.sucursales());
        List<CategoriaDatos> categorias = lista(documento.categorias());
        List<SubcategoriaDatos> subcategorias = lista(documento.subcategorias());
        List<ProductoDatos> productos = lista(documento.productos());
        List<RolDatos> roles = lista(documento.roles());
        List<RolPermisoDatos> permisos = lista(documento.rolPermisos());
        List<HistorialDatos> historial = lista(documento.historialSuscripcion());
        Set<Integer> idsSucursales = ids(sucursales, SucursalDatos::idSucursal, "sucursal");
        Set<Integer> idsCategorias = ids(categorias, CategoriaDatos::idCategoriaProducto, "categoria_producto");
        Set<Integer> idsSubcategorias = ids(subcategorias, SubcategoriaDatos::idSubcategoria, "subcategoria_producto");
        Set<Integer> idsProductos = ids(productos, ProductoDatos::idProducto, "producto");
        Set<Integer> idsRoles = ids(roles, RolDatos::idRol, "rol");
        ids(permisos, RolPermisoDatos::idRolPermiso, "rol_permiso");
        ids(historial, HistorialDatos::idHistorialSuscripcion, "historial_suscripcion");
        validar(idEmpresa, documento.empresa(), sucursales, categorias, subcategorias, productos, roles, permisos,
                documento.configuracionSistema(), historial, idsCategorias, idsSubcategorias, idsRoles, idsSucursales,
                idsProductos);
        entityManager.clear();

        actualizarEmpresa(idEmpresa, documento.empresa());
        soltar();
        renombrarCategoriasTemporales(idEmpresa);
        soltar();
        for (CategoriaDatos fila : categorias) {
            guardarCategoria(idEmpresa, fila);
        }
        soltar();
        for (SubcategoriaDatos fila : subcategorias) {
            guardarSubcategoria(idEmpresa, fila);
        }
        soltar();
        borrarSi("delete from Producto p where p.idEmpresa = :idEmpresa", "and p.idProducto not in :ids", idEmpresa, idsProductos);
        soltar();
        for (ProductoDatos fila : productos) {
            guardarProducto(idEmpresa, fila);
        }
        soltar();
        borrarSubcategoriasFuera(idEmpresa, idsSubcategorias);
        soltar();
        borrarSi("delete from CategoriaProducto c where c.idEmpresa = :idEmpresa",
                "and c.idCategoriaProducto not in :ids", idEmpresa, idsCategorias);
        soltar();
        for (SucursalDatos fila : sucursales) {
            guardarSucursal(idEmpresa, fila);
        }
        soltar();
        borrarSi("delete from Sucursal s where s.empresa.idEmpresa = :idEmpresa",
                "and s.idSucursal not in :ids", idEmpresa, idsSucursales);
        soltar();
        reemplazarRoles(idEmpresa, roles, permisos, idsRoles);
        soltar();
        guardarConfiguracion(idEmpresa, documento.configuracionSistema());
        soltar();
        borrarSi("delete from HistorialSuscripcion h where h.empresa.idEmpresa = :idEmpresa", "", idEmpresa, Set.of());
        soltar();
        for (HistorialDatos fila : historial) {
            insertarHistorial(idEmpresa, fila);
        }
        soltar();
        sincronizarIdentidades();
    }

    private void validar(
            Integer idEmpresa,
            EmpresaDatos empresa,
            List<SucursalDatos> sucursales,
            List<CategoriaDatos> categorias,
            List<SubcategoriaDatos> subcategorias,
            List<ProductoDatos> productos,
            List<RolDatos> roles,
            List<RolPermisoDatos> permisos,
            ConfiguracionDatos configuracion,
            List<HistorialDatos> historial,
            Set<Integer> idsCategorias,
            Set<Integer> idsSubcategorias,
            Set<Integer> idsRoles,
            Set<Integer> idsSucursales,
            Set<Integer> idsProductos) {
        if (empresa == null || empresa.idEmpresa() == null || !idEmpresa.equals(empresa.idEmpresa())) {
            throw ApiException.badRequest("El id_empresa del respaldo no coincide con la empresa de la URL.");
        }
        requerido(empresa.nombre(), "el nombre de la empresa");
        requerido(empresa.razonSocial(), "la razón social");
        String nit = requerido(empresa.nit(), "el NIT");
        String correo = requerido(empresa.correo(), "el correo");
        if (empresa.fechaCreacion() == null) {
            throw ApiException.badRequest("Falta la fecha de creación de la empresa.");
        }
        empresaRepository.findByNit(nit).ifPresent(otra -> {
            if (!idEmpresa.equals(otra.getIdEmpresa())) {
                throw ApiException.badRequest("El NIT pertenece a otra empresa.");
            }
        });
        empresaRepository.findByCorreoIgnoreCase(correo).ifPresent(otra -> {
            if (!idEmpresa.equals(otra.getIdEmpresa())) {
                throw ApiException.badRequest("El correo pertenece a otra empresa.");
            }
        });
        Set<String> nombresCategoria = new HashSet<>();
        for (CategoriaDatos fila : categorias) {
            exigirEmpresa(fila.idEmpresa(), idEmpresa, "categoría", fila.idCategoriaProducto());
            String nombre = requerido(fila.nombre(), "el nombre de la categoría " + fila.idCategoriaProducto());
            if (!nombresCategoria.add(nombre.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("Hay categorías repetidas con el nombre \"" + nombre + "\".");
            }
            exigirNombreCategoriaLibre(idEmpresa, nombre);
            categoriaProductoRepository.findById(fila.idCategoriaProducto()).ifPresent(actual -> {
                if (!idEmpresa.equals(actual.getIdEmpresa())) {
                    throw ApiException.badRequest("La categoría " + fila.idCategoriaProducto() + " pertenece a otra empresa.");
                }
            });
        }
        for (SubcategoriaDatos fila : subcategorias) {
            if (fila.idCategoriaProducto() == null || !idsCategorias.contains(fila.idCategoriaProducto())) {
                throw ApiException.badRequest("La subcategoría " + fila.idSubcategoria() + " no apunta a una categoría de este respaldo.");
            }
            requerido(fila.nombre(), "el nombre de la subcategoría " + fila.idSubcategoria());
            subcategoriaProductoRepository.findById(fila.idSubcategoria()).ifPresent(actual -> {
                Integer duena = actual.getCategoriaProducto() == null ? null : actual.getCategoriaProducto().getIdEmpresa();
                if (!idEmpresa.equals(duena)) {
                    throw ApiException.badRequest("La subcategoría " + fila.idSubcategoria() + " pertenece a otra empresa.");
                }
            });
        }
        for (ProductoDatos fila : productos) {
            exigirEmpresa(fila.idEmpresa(), idEmpresa, "producto", fila.idProducto());
            requerido(fila.nombre(), "el nombre del producto " + fila.idProducto());
            requerido(fila.unidadMedida(), "la unidad del producto " + fila.idProducto());
            if (fila.precio() == null) {
                throw ApiException.badRequest("Falta el precio del producto " + fila.idProducto() + ".");
            }
            if (fila.idSubcategoria() != null && !idsSubcategorias.contains(fila.idSubcategoria())) {
                throw ApiException.badRequest("El producto " + fila.idProducto() + " apunta a una subcategoría que no viene en el respaldo.");
            }
            productoRepository.findById(fila.idProducto()).ifPresent(actual -> {
                if (!idEmpresa.equals(actual.getIdEmpresa())) {
                    throw ApiException.badRequest("El producto " + fila.idProducto() + " pertenece a otra empresa.");
                }
            });
        }
        for (Producto actual : productoRepository.findByIdEmpresa(idEmpresa)) {
            if (!idsProductos.contains(actual.getIdProducto()) && productoEnUso(actual.getIdProducto())) {
                throw ApiException.badRequest("No se quita el producto \"" + actual.getNombre()
                        + "\" porque tiene stock, movimientos, ventas o pedidos. Este respaldo no borra ventas.");
            }
        }
        for (SucursalDatos fila : sucursales) {
            exigirEmpresa(fila.idEmpresa(), idEmpresa, "sucursal", fila.idSucursal());
            requerido(fila.nombre(), "el nombre de la sucursal " + fila.idSucursal());
            requerido(fila.direccion(), "la dirección de la sucursal " + fila.idSucursal());
            requerido(fila.telefono(), "el teléfono de la sucursal " + fila.idSucursal());
            requerido(fila.ciudad(), "la ciudad de la sucursal " + fila.idSucursal());
            if (fila.fechaRegistro() == null) {
                throw ApiException.badRequest("Falta la fecha de registro de la sucursal " + fila.idSucursal() + ".");
            }
            sucursalRepository.findById(fila.idSucursal()).ifPresent(actual -> {
                if (actual.getEmpresa() == null || !idEmpresa.equals(actual.getEmpresa().getIdEmpresa())) {
                    throw ApiException.badRequest("La sucursal " + fila.idSucursal() + " pertenece a otra empresa.");
                }
            });
        }
        for (Sucursal actual : sucursalRepository.findByEmpresa_IdEmpresa(idEmpresa)) {
            if (!idsSucursales.contains(actual.getIdSucursal()) && sucursalEnUso(idEmpresa, actual.getIdSucursal())) {
                throw ApiException.badRequest("No se quita la sucursal \"" + actual.getNombre()
                        + "\" porque tiene cajas, stock, pedidos o personal. No se borran ventas.");
            }
        }
        for (RolDatos fila : roles) {
            exigirEmpresa(fila.idEmpresa(), idEmpresa, "rol", fila.idRol());
            if (fila.tipo() == null || !"CUSTOM".equalsIgnoreCase(fila.tipo())) {
                throw ApiException.badRequest("El rol " + fila.idRol() + " no es un rol personalizado de la empresa.");
            }
            requerido(fila.nombre(), "el nombre del rol " + fila.idRol());
            rolRepository.findById(fila.idRol()).ifPresent(actual -> {
                if (!idEmpresa.equals(actual.getIdEmpresa()) || !"CUSTOM".equalsIgnoreCase(actual.getTipo())) {
                    throw ApiException.badRequest("El rol " + fila.idRol() + " no es un rol personalizado de esta empresa.");
                }
            });
        }
        for (Rol actual : rolesCustom(idEmpresa)) {
            if (!idsRoles.contains(actual.getIdRol()) && rolAsignado(actual.getIdRol())) {
                throw ApiException.badRequest("No se quita el rol \"" + actual.getNombre()
                        + "\" porque tiene usuarios asignados. No se modifican usuarios.");
            }
        }
        for (RolPermisoDatos fila : permisos) {
            if (fila.idRol() == null || !idsRoles.contains(fila.idRol())) {
                throw ApiException.badRequest("El permiso de rol " + fila.idRolPermiso() + " no apunta a un rol de este respaldo.");
            }
            if (fila.idPermiso() == null || !permisoRepository.existsById(fila.idPermiso())) {
                throw ApiException.badRequest("El permiso " + fila.idPermiso() + " no existe. No se crean permisos globales.");
            }
            rolPermisoRepository.findById(fila.idRolPermiso()).ifPresent(actual -> {
                Rol rol = actual.getRol();
                if (rol == null || !idEmpresa.equals(rol.getIdEmpresa()) || !"CUSTOM".equalsIgnoreCase(rol.getTipo())) {
                    throw ApiException.badRequest("El permiso de rol " + fila.idRolPermiso() + " pertenece a otra empresa.");
                }
            });
        }
        if (configuracion != null) {
            if (configuracion.idEmpresa() != null && !idEmpresa.equals(configuracion.idEmpresa())) {
                throw ApiException.badRequest("La configuración pertenece a otra empresa.");
            }
            if (configuracion.idConfiguracion() != null) {
                configuracionSistemaRepository.findById(configuracion.idConfiguracion()).ifPresent(actual -> {
                    if (actual.getEmpresa() == null || !idEmpresa.equals(actual.getEmpresa().getIdEmpresa())) {
                        throw ApiException.badRequest("La configuración " + configuracion.idConfiguracion() + " pertenece a otra empresa.");
                    }
                });
            }
        }
        Set<String> sesiones = new HashSet<>();
        for (HistorialDatos fila : historial) {
            exigirEmpresa(fila.idEmpresa(), idEmpresa, "suscripción", fila.idHistorialSuscripcion());
            if (fila.idPlan() == null || !planRepository.existsById(fila.idPlan())) {
                throw ApiException.badRequest("El plan " + fila.idPlan() + " no existe. No se modifican los planes.");
            }
            requerido(fila.estado(), "el estado de la suscripción " + fila.idHistorialSuscripcion());
            if (fila.fechaInicio() == null) {
                throw ApiException.badRequest("Falta la fecha de inicio de la suscripción " + fila.idHistorialSuscripcion() + ".");
            }
            if (fila.stripeSessionId() != null && !fila.stripeSessionId().isBlank()
                    && !sesiones.add(fila.stripeSessionId())) {
                throw ApiException.badRequest("Hay sesiones de pago repetidas en el respaldo.");
            }
            historialSuscripcionRepository.findById(fila.idHistorialSuscripcion()).ifPresent(actual -> {
                if (actual.getEmpresa() == null || !idEmpresa.equals(actual.getEmpresa().getIdEmpresa())) {
                    throw ApiException.badRequest("La suscripción " + fila.idHistorialSuscripcion() + " pertenece a otra empresa.");
                }
            });
            if (fila.stripeSessionId() != null && !fila.stripeSessionId().isBlank()) {
                historialSuscripcionRepository.findByStripeSessionId(fila.stripeSessionId()).ifPresent(actual -> {
                    if (actual.getEmpresa() == null || !idEmpresa.equals(actual.getEmpresa().getIdEmpresa())) {
                        throw ApiException.badRequest("La sesión de pago pertenece a otra empresa.");
                    }
                });
            }
        }
    }

    private void actualizarEmpresa(Integer idEmpresa, EmpresaDatos datos) {
        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
        empresa.setNombre(requerido(datos.nombre(), "el nombre de la empresa"));
        empresa.setRazonSocial(requerido(datos.razonSocial(), "la razón social"));
        empresa.setNit(requerido(datos.nit(), "el NIT"));
        empresa.setCorreo(requerido(datos.correo(), "el correo"));
        empresa.setFechaCreacion(datos.fechaCreacion());
        if (datos.activo() != null) {
            empresa.setActivo(datos.activo());
        }
    }

    private void renombrarCategoriasTemporales(Integer idEmpresa) {
        String marca = Long.toString(System.nanoTime());
        for (CategoriaProducto categoria : categoriaProductoRepository.findByIdEmpresa(idEmpresa)) {
            categoria.setNombre("__rsp_" + idEmpresa + "_" + categoria.getIdCategoriaProducto() + "_" + marca);
        }
    }

    private void guardarCategoria(Integer idEmpresa, CategoriaDatos datos) {
        CategoriaProducto categoria = categoriaProductoRepository.findById(datos.idCategoriaProducto()).orElse(null);
        if (categoria == null) {
            insertar("INSERT INTO categoria_producto (id_categoria_producto, id_empresa, nombre, descripcion, activo) VALUES (?, ?, ?, ?, ?)",
                    ps -> {
                        entero(ps, 1, datos.idCategoriaProducto());
                        entero(ps, 2, idEmpresa);
                        texto(ps, 3, requerido(datos.nombre(), "el nombre de la categoría"));
                        texto(ps, 4, opcional(datos.descripcion()));
                        ps.setBoolean(5, bool(datos.activo(), true));
                    });
            return;
        }
        categoria.setIdEmpresa(idEmpresa);
        categoria.setNombre(requerido(datos.nombre(), "el nombre de la categoría"));
        categoria.setDescripcion(opcional(datos.descripcion()));
        categoria.setActivo(bool(datos.activo(), true));
    }

    private void guardarSubcategoria(Integer idEmpresa, SubcategoriaDatos datos) {
        CategoriaProducto categoria = categoriaProductoRepository.findById(datos.idCategoriaProducto())
                .orElseThrow(() -> ApiException.badRequest("La categoría " + datos.idCategoriaProducto() + " no existe."));
        if (!idEmpresa.equals(categoria.getIdEmpresa())) {
            throw ApiException.badRequest("La categoría " + datos.idCategoriaProducto() + " no pertenece a esta empresa.");
        }
        SubcategoriaProducto subcategoria = subcategoriaProductoRepository.findById(datos.idSubcategoria()).orElse(null);
        if (subcategoria == null) {
            insertar("INSERT INTO subcategoria_producto (id_subcategoria, id_categoria_producto, nombre, descripcion, activo) VALUES (?, ?, ?, ?, ?)",
                    ps -> {
                        entero(ps, 1, datos.idSubcategoria());
                        entero(ps, 2, datos.idCategoriaProducto());
                        texto(ps, 3, requerido(datos.nombre(), "el nombre de la subcategoría"));
                        texto(ps, 4, opcional(datos.descripcion()));
                        ps.setBoolean(5, bool(datos.activo(), true));
                    });
            return;
        }
        subcategoria.setCategoriaProducto(categoria);
        subcategoria.setNombre(requerido(datos.nombre(), "el nombre de la subcategoría"));
        subcategoria.setDescripcion(opcional(datos.descripcion()));
        subcategoria.setActivo(bool(datos.activo(), true));
    }

    private void guardarProducto(Integer idEmpresa, ProductoDatos datos) {
        SubcategoriaProducto subcategoria = null;
        if (datos.idSubcategoria() != null) {
            subcategoria = subcategoriaProductoRepository.findById(datos.idSubcategoria())
                    .orElseThrow(() -> ApiException.badRequest("La subcategoría " + datos.idSubcategoria() + " no existe."));
            Integer duena = subcategoria.getCategoriaProducto() == null
                    ? null
                    : subcategoria.getCategoriaProducto().getIdEmpresa();
            if (!idEmpresa.equals(duena)) {
                throw ApiException.badRequest("La subcategoría " + datos.idSubcategoria() + " no pertenece a esta empresa.");
            }
        }
        Producto producto = productoRepository.findById(datos.idProducto()).orElse(null);
        if (producto == null) {
            Integer idSubcategoria = subcategoria == null ? null : subcategoria.getIdSubcategoria();
            insertar("""
                    INSERT INTO producto (
                        id_producto, id_empresa, id_subcategoria, nombre, codigo_barra,
                        descripcion, unidad_medida, precio, costo, imagen, activo
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, ps -> {
                entero(ps, 1, datos.idProducto());
                entero(ps, 2, idEmpresa);
                entero(ps, 3, idSubcategoria);
                texto(ps, 4, requerido(datos.nombre(), "el nombre del producto"));
                texto(ps, 5, opcional(datos.codigoBarra()));
                texto(ps, 6, opcional(datos.descripcion()));
                texto(ps, 7, requerido(datos.unidadMedida(), "la unidad del producto"));
                ps.setBigDecimal(8, datos.precio());
                decimal(ps, 9, datos.costo());
                texto(ps, 10, opcional(datos.imagen()));
                ps.setBoolean(11, bool(datos.activo(), true));
            });
            return;
        }
        producto.setIdEmpresa(idEmpresa);
        producto.setSubcategoria(subcategoria);
        producto.setNombre(requerido(datos.nombre(), "el nombre del producto"));
        producto.setCodigoBarra(opcional(datos.codigoBarra()));
        producto.setDescripcion(opcional(datos.descripcion()));
        producto.setUnidadMedida(requerido(datos.unidadMedida(), "la unidad del producto"));
        producto.setPrecio(datos.precio());
        producto.setCosto(datos.costo());
        producto.setImagen(opcional(datos.imagen()));
        producto.setActivo(bool(datos.activo(), true));
    }

    private void guardarSucursal(Integer idEmpresa, SucursalDatos datos) {
        Sucursal sucursal = sucursalRepository.findById(datos.idSucursal()).orElse(null);
        if (sucursal == null) {
            insertar("""
                    INSERT INTO sucursal (
                        id_sucursal, id_empresa, nombre, direccion, telefono, ciudad, fecha_registro, activo
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, ps -> {
                entero(ps, 1, datos.idSucursal());
                entero(ps, 2, idEmpresa);
                texto(ps, 3, requerido(datos.nombre(), "el nombre de la sucursal"));
                texto(ps, 4, requerido(datos.direccion(), "la dirección"));
                texto(ps, 5, requerido(datos.telefono(), "el teléfono"));
                texto(ps, 6, requerido(datos.ciudad(), "la ciudad"));
                fecha(ps, 7, datos.fechaRegistro());
                ps.setBoolean(8, bool(datos.activo(), true));
            });
            return;
        }
        if (sucursal.getEmpresa() == null || !idEmpresa.equals(sucursal.getEmpresa().getIdEmpresa())) {
            throw ApiException.badRequest("La sucursal " + datos.idSucursal() + " pertenece a otra empresa.");
        }
        sucursal.setNombre(requerido(datos.nombre(), "el nombre de la sucursal"));
        sucursal.setDireccion(requerido(datos.direccion(), "la dirección"));
        sucursal.setTelefono(requerido(datos.telefono(), "el teléfono"));
        sucursal.setCiudad(requerido(datos.ciudad(), "la ciudad"));
        sucursal.setFechaRegistro(datos.fechaRegistro());
        sucursal.setActivo(bool(datos.activo(), true));
    }

    private void reemplazarRoles(Integer idEmpresa, List<RolDatos> roles, List<RolPermisoDatos> permisos, Set<Integer> idsRoles) {
        List<Integer> idsCustom = rolesCustom(idEmpresa).stream().map(Rol::getIdRol).toList();
        if (!idsCustom.isEmpty()) {
            entityManager.createQuery("delete from RolPermiso rp where rp.rol.idRol in :ids")
                    .setParameter("ids", idsCustom)
                    .executeUpdate();
        }
        soltar();
        if (idsRoles.isEmpty()) {
            entityManager.createQuery("""
                    delete from Rol r
                    where r.idEmpresa = :idEmpresa
                      and upper(r.tipo) = 'CUSTOM'
                    """)
                    .setParameter("idEmpresa", idEmpresa)
                    .executeUpdate();
        } else {
            entityManager.createQuery("""
                    delete from Rol r
                    where r.idEmpresa = :idEmpresa
                      and upper(r.tipo) = 'CUSTOM'
                      and r.idRol not in :ids
                    """)
                    .setParameter("idEmpresa", idEmpresa)
                    .setParameter("ids", idsRoles)
                    .executeUpdate();
        }
        soltar();
        for (RolDatos fila : roles) {
            guardarRol(idEmpresa, fila);
        }
        soltar();
        for (RolPermisoDatos fila : permisos) {
            insertar("INSERT INTO rol_permiso (id_rol_permiso, id_rol, id_permiso, activo) VALUES (?, ?, ?, ?)", ps -> {
                entero(ps, 1, fila.idRolPermiso());
                entero(ps, 2, fila.idRol());
                entero(ps, 3, fila.idPermiso());
                ps.setBoolean(4, bool(fila.activo(), true));
            });
        }
    }

    private void guardarRol(Integer idEmpresa, RolDatos datos) {
        Rol rol = rolRepository.findById(datos.idRol()).orElse(null);
        String descripcion = opcional(datos.descripcion());
        if (descripcion == null) {
            descripcion = requerido(datos.nombre(), "el nombre del rol");
        }
        String nombre = requerido(datos.nombre(), "el nombre del rol");
        if (rol == null) {
            String descripcionFinal = descripcion;
            insertar("INSERT INTO rol (id_rol, nombre, id_empresa, tipo, descripcion, activo) VALUES (?, ?, ?, 'CUSTOM', ?, ?)",
                    ps -> {
                        entero(ps, 1, datos.idRol());
                        texto(ps, 2, nombre);
                        entero(ps, 3, idEmpresa);
                        texto(ps, 4, descripcionFinal);
                        ps.setBoolean(5, bool(datos.activo(), true));
                    });
            return;
        }
        rol.setNombre(nombre);
        rol.setIdEmpresa(idEmpresa);
        rol.setTipo("CUSTOM");
        rol.setDescripcion(descripcion);
        rol.setActivo(bool(datos.activo(), true));
    }

    private void guardarConfiguracion(Integer idEmpresa, ConfiguracionDatos datos) {
        ConfiguracionSistema actual = configuracionSistemaRepository.findByEmpresa_IdEmpresa(idEmpresa).orElse(null);
        if (datos == null) {
            if (actual != null) {
                configuracionSistemaRepository.delete(actual);
            }
            return;
        }
        // Una sola fila por empresa: si el id del archivo no coincide, se actualiza la fila de esta empresa.
        if (actual != null) {
            copiarConfiguracion(actual, datos);
            return;
        }
        Empresa empresa = empresaRepository.findById(idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Empresa no encontrada."));
        if (datos.idConfiguracion() == null) {
            ConfiguracionSistema nueva = new ConfiguracionSistema();
            nueva.setEmpresa(empresa);
            copiarConfiguracion(nueva, datos);
            configuracionSistemaRepository.save(nueva);
            return;
        }
        insertar("""
                INSERT INTO configuracion_sistema (
                    id_configuracion, id_empresa, tema, idioma, zona_horaria, moneda,
                    activar_notificaciones_push, activar_sonido, activar_vibracion,
                    confirmar_antes_de_eliminar, cerrar_sesion_por_inactividad, minutos_inactividad,
                    imprimir_automaticamente, numero_copias, tamano_ticket, color_primario
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ps -> {
            entero(ps, 1, datos.idConfiguracion());
            entero(ps, 2, idEmpresa);
            texto(ps, 3, textoO(datos.tema(), "claro"));
            texto(ps, 4, textoO(datos.idioma(), "es"));
            texto(ps, 5, textoO(datos.zonaHoraria(), "America/La_Paz"));
            texto(ps, 6, textoO(datos.moneda(), "BOB"));
            ps.setBoolean(7, bool(datos.activarNotificacionesPush(), true));
            ps.setBoolean(8, bool(datos.activarSonido(), true));
            ps.setBoolean(9, bool(datos.activarVibracion(), true));
            ps.setBoolean(10, bool(datos.confirmarAntesDeEliminar(), true));
            ps.setBoolean(11, bool(datos.cerrarSesionPorInactividad(), false));
            entero(ps, 12, datos.minutosInactividad() == null ? 15 : datos.minutosInactividad());
            ps.setBoolean(13, bool(datos.imprimirAutomaticamente(), false));
            entero(ps, 14, datos.numeroCopias() == null ? 1 : datos.numeroCopias());
            texto(ps, 15, textoO(datos.tamanoTicket(), "80mm"));
            texto(ps, 16, opcional(datos.colorPrimario()) == null ? "#047857" : datos.colorPrimario());
        });
    }

    private void copiarConfiguracion(ConfiguracionSistema destino, ConfiguracionDatos datos) {
        destino.setTema(textoO(datos.tema(), "claro"));
        destino.setIdioma(textoO(datos.idioma(), "es"));
        destino.setZonaHoraria(textoO(datos.zonaHoraria(), "America/La_Paz"));
        destino.setMoneda(textoO(datos.moneda(), "BOB"));
        destino.setActivarNotificacionesPush(bool(datos.activarNotificacionesPush(), true));
        destino.setActivarSonido(bool(datos.activarSonido(), true));
        destino.setActivarVibracion(bool(datos.activarVibracion(), true));
        destino.setConfirmarAntesDeEliminar(bool(datos.confirmarAntesDeEliminar(), true));
        destino.setCerrarSesionPorInactividad(bool(datos.cerrarSesionPorInactividad(), false));
        destino.setMinutosInactividad(datos.minutosInactividad() == null ? 15 : datos.minutosInactividad());
        destino.setImprimirAutomaticamente(bool(datos.imprimirAutomaticamente(), false));
        destino.setNumeroCopias(datos.numeroCopias() == null ? 1 : datos.numeroCopias());
        destino.setTamanoTicket(textoO(datos.tamanoTicket(), "80mm"));
        destino.setColorPrimario(opcional(datos.colorPrimario()) == null ? "#047857" : datos.colorPrimario().trim());
    }

    private void insertarHistorial(Integer idEmpresa, HistorialDatos datos) {
        insertar("""
                INSERT INTO historial_suscripcion (
                    id_historial_suscripcion, id_plan, id_empresa, fecha_inicio, fecha_fin,
                    estado, stripe_session_id, stripe_payment_intent_id, stripe_payment_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, ps -> {
            entero(ps, 1, datos.idHistorialSuscripcion());
            entero(ps, 2, datos.idPlan());
            entero(ps, 3, idEmpresa);
            fecha(ps, 4, datos.fechaInicio());
            fecha(ps, 5, datos.fechaFin());
            texto(ps, 6, requerido(datos.estado(), "el estado de la suscripción"));
            texto(ps, 7, opcional(datos.stripeSessionId()));
            texto(ps, 8, opcional(datos.stripePaymentIntentId()));
            texto(ps, 9, opcional(datos.stripePaymentStatus()));
        });
    }

    private void borrarSubcategoriasFuera(Integer idEmpresa, Set<Integer> ids) {
        if (ids.isEmpty()) {
            entityManager.createQuery("""
                    delete from SubcategoriaProducto s
                    where s.categoriaProducto.idEmpresa = :idEmpresa
                    """)
                    .setParameter("idEmpresa", idEmpresa)
                    .executeUpdate();
            return;
        }
        entityManager.createQuery("""
                delete from SubcategoriaProducto s
                where s.categoriaProducto.idEmpresa = :idEmpresa
                  and s.idSubcategoria not in :ids
                """)
                .setParameter("idEmpresa", idEmpresa)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private void borrarSi(String jpqlBase, String jpqlExtra, Integer idEmpresa, Set<Integer> ids) {
        if (ids == null || ids.isEmpty() || jpqlExtra.isBlank()) {
            entityManager.createQuery(jpqlBase)
                    .setParameter("idEmpresa", idEmpresa)
                    .executeUpdate();
            return;
        }
        entityManager.createQuery(jpqlBase + " " + jpqlExtra)
                .setParameter("idEmpresa", idEmpresa)
                .setParameter("ids", ids)
                .executeUpdate();
    }

    private boolean productoEnUso(Integer idProducto) {
        long usos = contar("select count(s) from Stock s where s.producto.idProducto = :id", idProducto)
                + contar("select count(m) from MovimientoInventario m where m.producto.idProducto = :id", idProducto)
                + contar("select count(d) from DetalleVenta d where d.idProducto = :id", idProducto)
                + contar("select count(d) from PedidoClienteDetalle d where d.idProducto = :id", idProducto);
        return usos > 0;
    }

    private boolean sucursalEnUso(Integer idEmpresa, Integer idSucursal) {
        long cajas = contar("select count(c) from Caja c where c.sucursal.idSucursal = :id", idSucursal);
        long stock = contar("select count(s) from Stock s where s.idSucursal = :id", idSucursal);
        long movimientos = contar("select count(m) from MovimientoInventario m where m.idSucursal = :id", idSucursal);
        long pedidos = entityManager.createQuery(
                "select count(p) from PedidoCliente p where p.idSucursal = :id and p.idEmpresa = :idEmpresa", Long.class)
                .setParameter("id", idSucursal)
                .setParameter("idEmpresa", idEmpresa)
                .getSingleResult();
        long personal = entityManager.createQuery(
                "select count(ur) from UsuarioRol ur where ur.idSucursal = :id and ur.idEmpresa = :idEmpresa", Long.class)
                .setParameter("id", idSucursal)
                .setParameter("idEmpresa", idEmpresa)
                .getSingleResult();
        return cajas + stock + movimientos + pedidos + personal > 0;
    }

    private boolean rolAsignado(Integer idRol) {
        return contar("select count(ur) from UsuarioRol ur where ur.rol.idRol = :id", idRol) > 0;
    }

    private long contar(String jpql, Integer id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }

    private void exigirNombreCategoriaLibre(Integer idEmpresa, String nombre) {
        Long otras = entityManager.createQuery("""
                select count(c) from CategoriaProducto c
                where lower(c.nombre) = :nombre
                  and (c.idEmpresa is null or c.idEmpresa <> :idEmpresa)
                """, Long.class)
                .setParameter("nombre", nombre.toLowerCase(Locale.ROOT))
                .setParameter("idEmpresa", idEmpresa)
                .getSingleResult();
        if (otras > 0) {
            throw ApiException.badRequest("La categoría \"" + nombre + "\" ya existe fuera de esta empresa.");
        }
    }

    private void sincronizarIdentidades() {
        sincronizarIdentidad("categoria_producto", "id_categoria_producto");
        sincronizarIdentidad("subcategoria_producto", "id_subcategoria");
        sincronizarIdentidad("producto", "id_producto");
        sincronizarIdentidad("sucursal", "id_sucursal");
        sincronizarIdentidad("rol", "id_rol");
        sincronizarIdentidad("rol_permiso", "id_rol_permiso");
        sincronizarIdentidad("configuracion_sistema", "id_configuracion");
        sincronizarIdentidad("historial_suscripcion", "id_historial_suscripcion");
    }

    private void sincronizarIdentidad(String tabla, String columna) {
        if (!IDENTIDAD.contains(tabla + "." + columna)) {
            throw new IllegalStateException("Identidad no permitida.");
        }
        Number maximo = (Number) entityManager
                .createNativeQuery("SELECT COALESCE(MAX(" + columna + "), 0) FROM " + tabla)
                .getSingleResult();
        long siguiente = maximo.longValue() + 1;
        String motor = entityManager.unwrap(Session.class).doReturningWork(connection ->
                connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT));
        String sql;
        if (motor.contains("mysql") || motor.contains("mariadb")) {
            sql = "ALTER TABLE " + tabla + " AUTO_INCREMENT = " + siguiente;
        } else {
            sql = "ALTER TABLE " + tabla + " ALTER COLUMN " + columna + " RESTART WITH " + siguiente;
        }
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
    }

    private void insertar(String sql, SqlBinder binder) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.enlazar(statement);
                statement.executeUpdate();
            }
        });
    }

    private void soltar() {
        entityManager.flush();
        entityManager.clear();
    }

    private RespaldoDocumento parsear(byte[] bytes) {
        try {
            RespaldoDocumento documento = objectMapper.readValue(bytes, RespaldoDocumento.class);
            if (documento == null) {
                throw ApiException.badRequest("El archivo de respaldo está vacío.");
            }
            return documento;
        } catch (ApiException ex) {
            throw ex;
        } catch (JacksonException ex) {
            throw ApiException.badRequest("El archivo de respaldo no tiene un JSON válido.");
        }
    }

    private List<Rol> rolesCustom(Integer idEmpresa) {
        return rolRepository.findByIdEmpresa(idEmpresa).stream()
                .filter(rol -> "CUSTOM".equalsIgnoreCase(rol.getTipo()))
                .toList();
    }

    private EmpresaDatos empresaDatos(Empresa empresa) {
        return new EmpresaDatos(
                empresa.getIdEmpresa(),
                empresa.getNombre(),
                empresa.getRazonSocial(),
                empresa.getNit(),
                empresa.getCorreo(),
                empresa.getFechaCreacion(),
                empresa.getActivo());
    }

    private SucursalDatos sucursalDatos(Sucursal sucursal) {
        return new SucursalDatos(
                sucursal.getIdSucursal(),
                sucursal.getEmpresa().getIdEmpresa(),
                sucursal.getNombre(),
                sucursal.getDireccion(),
                sucursal.getTelefono(),
                sucursal.getCiudad(),
                sucursal.getFechaRegistro(),
                sucursal.getActivo());
    }

    private CategoriaDatos categoriaDatos(CategoriaProducto categoria) {
        return new CategoriaDatos(
                categoria.getIdCategoriaProducto(),
                categoria.getIdEmpresa(),
                categoria.getNombre(),
                categoria.getDescripcion(),
                categoria.getActivo());
    }

    private SubcategoriaDatos subcategoriaDatos(SubcategoriaProducto subcategoria) {
        return new SubcategoriaDatos(
                subcategoria.getIdSubcategoria(),
                subcategoria.getCategoriaProducto().getIdCategoriaProducto(),
                subcategoria.getNombre(),
                subcategoria.getDescripcion(),
                subcategoria.getActivo());
    }

    private ProductoDatos productoDatos(Producto producto) {
        Integer idSubcategoria = producto.getSubcategoria() == null ? null : producto.getSubcategoria().getIdSubcategoria();
        return new ProductoDatos(
                producto.getIdProducto(),
                producto.getIdEmpresa(),
                idSubcategoria,
                producto.getNombre(),
                producto.getCodigoBarra(),
                producto.getDescripcion(),
                producto.getUnidadMedida(),
                producto.getPrecio(),
                producto.getCosto(),
                producto.getImagen(),
                producto.getActivo());
    }

    private RolDatos rolDatos(Rol rol) {
        return new RolDatos(
                rol.getIdRol(),
                rol.getIdEmpresa(),
                rol.getNombre(),
                rol.getTipo(),
                rol.getDescripcion(),
                rol.getActivo());
    }

    private ConfiguracionDatos configuracionDatos(ConfiguracionSistema config) {
        return new ConfiguracionDatos(
                config.getIdConfiguracion(),
                config.getEmpresa().getIdEmpresa(),
                config.getTema(),
                config.getIdioma(),
                config.getZonaHoraria(),
                config.getMoneda(),
                config.getActivarNotificacionesPush(),
                config.getActivarSonido(),
                config.getActivarVibracion(),
                config.getConfirmarAntesDeEliminar(),
                config.getCerrarSesionPorInactividad(),
                config.getMinutosInactividad(),
                config.getImprimirAutomaticamente(),
                config.getNumeroCopias(),
                config.getTamanoTicket(),
                config.getColorPrimario());
    }

    private HistorialDatos historialDatos(HistorialSuscripcion historial) {
        return new HistorialDatos(
                historial.getIdHistorialSuscripcion(),
                historial.getPlan().getIdPlan(),
                historial.getEmpresa().getIdEmpresa(),
                historial.getFechaInicio(),
                historial.getFechaFin(),
                historial.getEstado(),
                historial.getStripeSessionId(),
                historial.getStripePaymentIntentId(),
                historial.getStripePaymentStatus());
    }

    private static void exigirEmpresa(Integer idEnFila, Integer idEmpresa, String tabla, Integer idFila) {
        if (idEnFila == null || !idEmpresa.equals(idEnFila)) {
            throw ApiException.badRequest("La fila " + idFila + " de " + tabla + " no pertenece a esta empresa.");
        }
    }

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }

    private static <T> Set<Integer> ids(List<T> filas, Function<T, Integer> lector, String tabla) {
        Set<Integer> resultado = new LinkedHashSet<>();
        for (T fila : filas) {
            Integer id = lector.apply(fila);
            if (id == null) {
                throw ApiException.badRequest("Cada fila de " + tabla + " debe traer su id.");
            }
            if (!resultado.add(id)) {
                throw ApiException.badRequest("Hay ids repetidos en " + tabla + ".");
            }
        }
        return resultado;
    }

    private static String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw ApiException.badRequest("Falta " + campo + ".");
        }
        return valor.trim();
    }

    private static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private static String textoO(String valor, String porDefecto) {
        String limpio = opcional(valor);
        return limpio == null ? porDefecto : limpio;
    }

    private static boolean bool(Boolean valor, boolean porDefecto) {
        return valor == null ? porDefecto : valor;
    }

    private static void entero(PreparedStatement statement, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            statement.setNull(indice, Types.INTEGER);
        } else {
            statement.setInt(indice, valor);
        }
    }

    private static void texto(PreparedStatement statement, int indice, String valor) throws SQLException {
        if (valor == null) {
            statement.setNull(indice, Types.VARCHAR);
        } else {
            statement.setString(indice, valor);
        }
    }

    private static void decimal(PreparedStatement statement, int indice, BigDecimal valor) throws SQLException {
        if (valor == null) {
            statement.setNull(indice, Types.DECIMAL);
        } else {
            statement.setBigDecimal(indice, valor);
        }
    }

    private static void fecha(PreparedStatement statement, int indice, LocalDate valor) throws SQLException {
        if (valor == null) {
            statement.setNull(indice, Types.DATE);
        } else {
            statement.setObject(indice, valor);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void enlazar(PreparedStatement statement) throws SQLException;
    }
}
