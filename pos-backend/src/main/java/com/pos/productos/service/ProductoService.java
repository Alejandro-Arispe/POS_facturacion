package com.pos.productos.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.inventario.service.InventarioService;
import com.pos.productos.domain.CategoriaProducto;
import com.pos.productos.domain.Producto;
import com.pos.productos.domain.SubcategoriaProducto;
import com.pos.productos.dto.ProductoDtos;
import com.pos.productos.repo.CategoriaProductoRepository;
import com.pos.productos.repo.ProductoRepository;
import com.pos.productos.repo.SubcategoriaProductoRepository;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.repo.UsuarioRolRepository;
import com.pos.usuarios.service.PrivilegioCampoService;

@Service
public class ProductoService {

    private final EmpresaAccess empresaAccess;
    private final CategoriaProductoRepository categoriaRepository;
    private final SubcategoriaProductoRepository subcategoriaRepository;
    private final ProductoRepository productoRepository;
    private final InventarioService inventarioService;
    private final UsuarioRolRepository usuarioRolRepository;
    private final PrivilegioCampoService privilegioCampoService;

    public ProductoService(
            EmpresaAccess empresaAccess,
            CategoriaProductoRepository categoriaRepository,
            SubcategoriaProductoRepository subcategoriaRepository,
            ProductoRepository productoRepository,
            InventarioService inventarioService,
            UsuarioRolRepository usuarioRolRepository,
            PrivilegioCampoService privilegioCampoService) {
        this.empresaAccess = empresaAccess;
        this.categoriaRepository = categoriaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.productoRepository = productoRepository;
        this.inventarioService = inventarioService;
        this.usuarioRolRepository = usuarioRolRepository;
        this.privilegioCampoService = privilegioCampoService;
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.CategoriaResponse> listarCategorias() {
        return categoriaRepository.findAllByOrderByNombreAsc().stream().map(this::toCategoria).toList();
    }

    @Transactional
    public ProductoDtos.CategoriaResponse crearCategoria(Usuario usuario, Integer idEmpresa, ProductoDtos.CategoriaCreate datos) {
        if (idEmpresa != null) {
            empresaAccess.requireEmpresa(usuario, idEmpresa);
        } else {
            empresaAccess.requireUsuarioActivo(usuario);
        }
        CategoriaProducto categoria = new CategoriaProducto();
        categoria.setIdEmpresa(idEmpresa);
        categoria.setNombre(datos.getNombre());
        categoria.setDescripcion(datos.getDescripcion());
        categoria.setActivo(datos.getActivo() == null || datos.getActivo());
        try {
            return toCategoria(categoriaRepository.save(categoria));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("Ya existe una categoria con ese nombre.");
        }
    }

    @Transactional(readOnly = true)
    public ProductoDtos.CategoriaResponse obtenerCategoria(Integer id) {
        return toCategoria(categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria no encontrada.")));
    }

    @Transactional
    public ProductoDtos.CategoriaResponse actualizarCategoria(Integer id, ProductoDtos.CategoriaUpdate datos) {
        CategoriaProducto categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria no encontrada."));
        if (datos.getNombre() != null) {
            categoria.setNombre(datos.getNombre());
        }
        if (datos.getDescripcion() != null) {
            categoria.setDescripcion(datos.getDescripcion());
        }
        if (datos.getActivo() != null) {
            categoria.setActivo(datos.getActivo());
        }
        try {
            return toCategoria(categoriaRepository.save(categoria));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("Ya existe una categoria con ese nombre.");
        }
    }

    @Transactional
    public ProductoDtos.SubcategoriaResponse crearSubcategoria(ProductoDtos.SubcategoriaCreate datos) {
        CategoriaProducto categoria = requireCategoriaActiva(datos.getIdCategoriaProducto());
        SubcategoriaProducto sub = new SubcategoriaProducto();
        sub.setCategoriaProducto(categoria);
        sub.setNombre(datos.getNombre());
        sub.setDescripcion(datos.getDescripcion());
        sub.setActivo(datos.getActivo() == null || datos.getActivo());
        return toSubcategoria(subcategoriaRepository.save(sub));
    }

    @Transactional(readOnly = true)
    public ProductoDtos.SubcategoriaResponse obtenerSubcategoria(Integer id) {
        return toSubcategoria(subcategoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subcategoria no encontrada.")));
    }

    @Transactional
    public ProductoDtos.SubcategoriaResponse actualizarSubcategoria(Integer id, ProductoDtos.SubcategoriaUpdate datos) {
        SubcategoriaProducto sub = subcategoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subcategoria no encontrada."));
        if (datos.getIdCategoriaProducto() != null) {
            sub.setCategoriaProducto(requireCategoriaActiva(datos.getIdCategoriaProducto()));
        }
        if (datos.getNombre() != null) {
            sub.setNombre(datos.getNombre());
        }
        if (datos.getDescripcion() != null) {
            sub.setDescripcion(datos.getDescripcion());
        }
        if (datos.getActivo() != null) {
            sub.setActivo(datos.getActivo());
        }
        return toSubcategoria(subcategoriaRepository.save(sub));
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.ProductoResponse> listarProductos(Usuario usuario) {
        empresaAccess.requireUsuarioActivo(usuario);
        Set<Integer> empresas = empresasDelUsuario(usuario);
        if (empresas.isEmpty()) {
            return List.of();
        }
        Map<Integer, Map<String, String>> estadosPorEmpresa = new HashMap<>();
        List<ProductoDtos.ProductoResponse> resultado = new ArrayList<>();
        for (Producto producto : productoRepository.findByIdEmpresaInOrderByNombreAsc(empresas)) {
            Map<String, String> estados = estadosPorEmpresa.computeIfAbsent(
                    producto.getIdEmpresa(),
                    idEmpresa -> privilegioCampoService.estadosProducto(usuario, idEmpresa));
            resultado.add(toProducto(producto, estados));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public ProductoDtos.ProductoResponse obtenerProducto(Usuario usuario, Integer idProducto) {
        Producto producto = requireProductoDeUsuario(usuario, idProducto);
        return toProducto(producto, privilegioCampoService.estadosProducto(usuario, producto.getIdEmpresa()));
    }

    @Transactional
    public ProductoDtos.ProductoResponse actualizarProducto(Usuario usuario, Integer idProducto, ProductoDtos.ProductoUpdate datos) {
        Producto producto = requireProductoDeUsuario(usuario, idProducto);
        if (!Boolean.TRUE.equals(producto.getActivo()) && Boolean.TRUE.equals(datos.getActivo())) {
            empresaAccess.requireProductCapacity(producto.getIdEmpresa());
        }
        Map<String, String> estados = privilegioCampoService.estadosProducto(usuario, producto.getIdEmpresa());
        if (!privilegioCampoService.permiteEditar(estados)) {
            throw ApiException.forbidden("No tienes permiso para editar este producto.");
        }
        if (datos.getIdSubcategoria() != null) {
            producto.setSubcategoria(requireSubcategoriaActiva(datos.getIdSubcategoria()));
        }
        if (datos.getNombre() != null) {
            producto.setNombre(datos.getNombre());
        }
        if (datos.getCodigoBarra() != null) {
            producto.setCodigoBarra(datos.getCodigoBarra());
        }
        if (datos.getDescripcion() != null) {
            producto.setDescripcion(datos.getDescripcion());
        }
        if (datos.getUnidadMedida() != null) {
            producto.setUnidadMedida(datos.getUnidadMedida());
        }
        if (privilegioCampoService.permiteEscribir(estados, "precio") && datos.getPrecio() != null) {
            producto.setPrecio(datos.getPrecio());
        }
        if (privilegioCampoService.permiteEscribir(estados, "costo")) {
            producto.setCosto(datos.getCosto());
        }
        if (datos.getActivo() != null) {
            producto.setActivo(datos.getActivo());
        }
        return toProducto(productoRepository.save(producto), estados);
    }

    @Transactional
    public ProductoDtos.ProductoResponse actualizarImagen(Usuario usuario, Integer idProducto, String imagen) {
        Producto producto = requireProductoDeUsuario(usuario, idProducto);
        Map<String, String> estados = privilegioCampoService.estadosProducto(usuario, producto.getIdEmpresa());
        if (!privilegioCampoService.permiteEditar(estados)) {
            throw ApiException.forbidden("No tienes permiso para editar este producto.");
        }
        producto.setImagen(imagen);
        return toProducto(productoRepository.save(producto), estados);
    }

    @Transactional
    public Map<String, String> eliminarProducto(Usuario usuario, Integer idProducto) {
        Producto producto = requireProductoDeUsuario(usuario, idProducto);
        Map<String, String> estados = privilegioCampoService.estadosProducto(usuario, producto.getIdEmpresa());
        if (!privilegioCampoService.permiteEliminar(estados)) {
            throw ApiException.forbidden("No tienes permiso para eliminar este producto.");
        }
        productoRepository.delete(producto);
        return Map.of("mensaje", "Producto eliminado correctamente.");
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.ProductoResponse> listarPorEmpresa(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        Map<String, String> estados = privilegioCampoService.estadosProducto(usuario, idEmpresa);
        return productoRepository.findByIdEmpresaOrderByNombreAsc(idEmpresa).stream()
                .map(producto -> toProducto(producto, estados))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.SubcategoriaResponse> listarSubcategoriasPorEmpresa(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return subcategoriaRepository.findByIdEmpresa(idEmpresa).stream().map(this::toSubcategoria).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.CategoriaResponse> listarCategoriasConSubcategorias(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        List<CategoriaProducto> categorias = categoriaRepository.findByIdEmpresaOrderByNombreAsc(idEmpresa);
        List<SubcategoriaProducto> subs = subcategoriaRepository.findByIdEmpresa(idEmpresa);
        Map<Integer, List<SubcategoriaProducto>> porCategoria = subs.stream()
                .collect(Collectors.groupingBy(s -> s.getCategoriaProducto().getIdCategoriaProducto()));
        List<ProductoDtos.CategoriaResponse> result = new ArrayList<>();
        for (CategoriaProducto categoria : categorias) {
            ProductoDtos.CategoriaResponse dto = toCategoria(categoria);
            dto.setSubcategorias(porCategoria.getOrDefault(categoria.getIdCategoriaProducto(), List.of())
                    .stream().map(this::toSubcategoria).toList());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public ProductoDtos.ProductoResponse crearProducto(
            Usuario usuario, Integer idEmpresa, ProductoDtos.ProductoCreate payload, String imagen) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        if (!Boolean.FALSE.equals(payload.getActivo())) empresaAccess.requireProductCapacity(idEmpresa);
        SubcategoriaProducto sub = requireSubcategoriaActiva(payload.getIdSubcategoria());
        if (sub.getCategoriaProducto() == null || !idEmpresa.equals(sub.getCategoriaProducto().getIdEmpresa())) {
            throw ApiException.badRequest("La subcategoria no pertenece a la empresa indicada.");
        }
        Producto producto = new Producto();
        producto.setIdEmpresa(idEmpresa);
        producto.setSubcategoria(sub);
        producto.setNombre(payload.getNombre());
        producto.setCodigoBarra(payload.getCodigoBarra());
        producto.setDescripcion(payload.getDescripcion());
        producto.setUnidadMedida(payload.getUnidadMedida());
        Map<String, String> estados = privilegioCampoService.estadosProducto(usuario, idEmpresa);
        if (privilegioCampoService.permiteEscribir(estados, "precio")) {
            producto.setPrecio(payload.getPrecio() == null ? BigDecimal.ZERO : payload.getPrecio());
        } else {
            producto.setPrecio(BigDecimal.ZERO);
        }
        if (privilegioCampoService.permiteEscribir(estados, "costo")) {
            producto.setCosto(payload.getCosto());
        }
        producto.setImagen(imagen);
        producto.setActivo(payload.getActivo() == null || payload.getActivo());
        producto = productoRepository.save(producto);
        inventarioService.sincronizarStocksPorProducto(producto.getIdProducto(), idEmpresa);
        return toProducto(productoRepository.findById(producto.getIdProducto()).orElse(producto), estados);
    }

    private CategoriaProducto requireCategoriaActiva(Integer id) {
        CategoriaProducto categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria no encontrada."));
        if (!Boolean.TRUE.equals(categoria.getActivo())) {
            throw ApiException.badRequest("La categoria esta inactiva.");
        }
        return categoria;
    }

    private SubcategoriaProducto requireSubcategoriaActiva(Integer id) {
        SubcategoriaProducto sub = subcategoriaRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Subcategoria no encontrada."));
        if (!Boolean.TRUE.equals(sub.getActivo())) {
            throw ApiException.badRequest("La subcategoria esta inactiva.");
        }
        return sub;
    }

    private ProductoDtos.CategoriaResponse toCategoria(CategoriaProducto categoria) {
        ProductoDtos.CategoriaResponse dto = new ProductoDtos.CategoriaResponse();
        dto.setIdCategoriaProducto(categoria.getIdCategoriaProducto());
        dto.setIdEmpresa(categoria.getIdEmpresa());
        dto.setNombre(categoria.getNombre());
        dto.setDescripcion(categoria.getDescripcion());
        dto.setActivo(categoria.getActivo());
        return dto;
    }

    private ProductoDtos.SubcategoriaResponse toSubcategoria(SubcategoriaProducto sub) {
        ProductoDtos.SubcategoriaResponse dto = new ProductoDtos.SubcategoriaResponse();
        dto.setIdSubcategoria(sub.getIdSubcategoria());
        dto.setIdCategoriaProducto(sub.getCategoriaProducto().getIdCategoriaProducto());
        dto.setNombre(sub.getNombre());
        dto.setDescripcion(sub.getDescripcion());
        dto.setActivo(sub.getActivo());
        return dto;
    }

    private Producto requireProductoDeUsuario(Usuario usuario, Integer idProducto) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        if (producto.getIdEmpresa() == null) {
            throw ApiException.notFound("Producto no encontrado.");
        }
        empresaAccess.requireEmpresa(usuario, producto.getIdEmpresa());
        return producto;
    }

    private Set<Integer> empresasDelUsuario(Usuario usuario) {
        Set<Integer> empresas = new HashSet<>();
        for (UsuarioRol relacion : usuarioRolRepository.findByUsuario_IdUsuarioAndActivoTrue(usuario.getIdUsuario())) {
            if (relacion.getIdEmpresa() != null) {
                empresas.add(relacion.getIdEmpresa());
            }
        }
        return empresas;
    }

    private ProductoDtos.ProductoResponse toProducto(Producto producto, Map<String, String> estados) {
        ProductoDtos.ProductoResponse dto = new ProductoDtos.ProductoResponse();
        dto.setIdProducto(producto.getIdProducto());
        dto.setIdEmpresa(producto.getIdEmpresa());
        dto.setIdSubcategoria(producto.getSubcategoria() == null ? null : producto.getSubcategoria().getIdSubcategoria());
        dto.setNombre(producto.getNombre());
        dto.setCodigoBarra(producto.getCodigoBarra());
        dto.setDescripcion(producto.getDescripcion());
        dto.setUnidadMedida(producto.getUnidadMedida() == null ? "" : producto.getUnidadMedida());
        if (privilegioCampoService.estaOculto(estados, "precio")) {
            dto.setPrecio(null);
        } else {
            dto.setPrecio(producto.getPrecio());
        }
        if (privilegioCampoService.estaOculto(estados, "costo")) {
            dto.setCosto(null);
        } else {
            dto.setCosto(producto.getCosto());
        }
        dto.setImagen(producto.getImagen());
        dto.setActivo(producto.getActivo());
        if (producto.getSubcategoria() != null) {
            dto.setSubcategoria(toSubcategoria(producto.getSubcategoria()));
        }
        return dto;
    }
}
