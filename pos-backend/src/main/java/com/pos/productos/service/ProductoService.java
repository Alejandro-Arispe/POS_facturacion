package com.pos.productos.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

@Service
public class ProductoService {

    private final EmpresaAccess empresaAccess;
    private final CategoriaProductoRepository categoriaRepository;
    private final SubcategoriaProductoRepository subcategoriaRepository;
    private final ProductoRepository productoRepository;
    private final InventarioService inventarioService;

    public ProductoService(
            EmpresaAccess empresaAccess,
            CategoriaProductoRepository categoriaRepository,
            SubcategoriaProductoRepository subcategoriaRepository,
            ProductoRepository productoRepository,
            InventarioService inventarioService) {
        this.empresaAccess = empresaAccess;
        this.categoriaRepository = categoriaRepository;
        this.subcategoriaRepository = subcategoriaRepository;
        this.productoRepository = productoRepository;
        this.inventarioService = inventarioService;
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
        return productoRepository.findAll().stream().map(this::toProducto).toList();
    }

    @Transactional(readOnly = true)
    public ProductoDtos.ProductoResponse obtenerProducto(Usuario usuario, Integer idProducto) {
        empresaAccess.requireUsuarioActivo(usuario);
        return toProducto(productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado.")));
    }

    @Transactional
    public ProductoDtos.ProductoResponse actualizarProducto(Usuario usuario, Integer idProducto, ProductoDtos.ProductoUpdate datos) {
        empresaAccess.requireUsuarioActivo(usuario);
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
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
        if (datos.getPrecio() != null) {
            producto.setPrecio(datos.getPrecio());
        }
        if (datos.getActivo() != null) {
            producto.setActivo(datos.getActivo());
        }
        return toProducto(productoRepository.save(producto));
    }

    @Transactional
    public ProductoDtos.ProductoResponse actualizarImagen(Usuario usuario, Integer idProducto, String imagen) {
        empresaAccess.requireUsuarioActivo(usuario);
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        producto.setImagen(imagen);
        return toProducto(productoRepository.save(producto));
    }

    @Transactional
    public Map<String, String> eliminarProducto(Usuario usuario, Integer idProducto) {
        empresaAccess.requireUsuarioActivo(usuario);
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
        productoRepository.delete(producto);
        return Map.of("mensaje", "Producto eliminado correctamente.");
    }

    @Transactional(readOnly = true)
    public List<ProductoDtos.ProductoResponse> listarPorEmpresa(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return productoRepository.findByIdEmpresaOrderByNombreAsc(idEmpresa).stream().map(this::toProducto).toList();
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
        producto.setPrecio(payload.getPrecio() == null ? BigDecimal.ZERO : payload.getPrecio());
        producto.setImagen(imagen);
        producto.setActivo(payload.getActivo() == null || payload.getActivo());
        producto = productoRepository.save(producto);
        inventarioService.sincronizarStocksPorProducto(producto.getIdProducto(), idEmpresa);
        return toProducto(productoRepository.findById(producto.getIdProducto()).orElse(producto));
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

    private ProductoDtos.ProductoResponse toProducto(Producto producto) {
        ProductoDtos.ProductoResponse dto = new ProductoDtos.ProductoResponse();
        dto.setIdProducto(producto.getIdProducto());
        dto.setIdEmpresa(producto.getIdEmpresa());
        dto.setIdSubcategoria(producto.getSubcategoria() == null ? null : producto.getSubcategoria().getIdSubcategoria());
        dto.setNombre(producto.getNombre());
        dto.setCodigoBarra(producto.getCodigoBarra());
        dto.setDescripcion(producto.getDescripcion());
        dto.setUnidadMedida(producto.getUnidadMedida() == null ? "" : producto.getUnidadMedida());
        dto.setPrecio(producto.getPrecio());
        dto.setImagen(producto.getImagen());
        dto.setActivo(producto.getActivo());
        if (producto.getSubcategoria() != null) {
            dto.setSubcategoria(toSubcategoria(producto.getSubcategoria()));
        }
        return dto;
    }
}
