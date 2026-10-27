package com.pos.productos.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pos.common.security.SecurityUtils;
import com.pos.common.storage.ProductImageStorage;
import com.pos.productos.dto.ProductoDtos;
import com.pos.productos.service.ProductoService;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final ProductPayloads payloads;

    public ProductoController(ProductoService productoService, ProductPayloads payloads) {
        this.productoService = productoService;
        this.payloads = payloads;
    }

    @GetMapping("/categorias")
    public List<ProductoDtos.CategoriaResponse> listarCategorias() {
        return productoService.listarCategorias();
    }

    @PostMapping("/categorias")
    public ProductoDtos.CategoriaResponse crearCategoria(@RequestBody ProductoDtos.CategoriaCreate datos) {
        return productoService.crearCategoria(SecurityUtils.currentUser(), datos.getIdEmpresa(), datos);
    }

    @GetMapping("/categorias/{id}")
    public ProductoDtos.CategoriaResponse obtenerCategoria(@PathVariable("id") Integer id) {
        return productoService.obtenerCategoria(id);
    }

    @PutMapping("/categorias/{id}")
    public ProductoDtos.CategoriaResponse actualizarCategoria(
            @PathVariable("id") Integer id, @RequestBody ProductoDtos.CategoriaUpdate datos) {
        return productoService.actualizarCategoria(id, datos);
    }

    @PostMapping("/subcategorias")
    public ProductoDtos.SubcategoriaResponse crearSubcategoria(@RequestBody ProductoDtos.SubcategoriaCreate datos) {
        return productoService.crearSubcategoria(datos);
    }

    @GetMapping("/subcategorias/{id}")
    public ProductoDtos.SubcategoriaResponse obtenerSubcategoria(@PathVariable("id") Integer id) {
        return productoService.obtenerSubcategoria(id);
    }

    @PutMapping("/subcategorias/{id}")
    public ProductoDtos.SubcategoriaResponse actualizarSubcategoria(
            @PathVariable("id") Integer id, @RequestBody ProductoDtos.SubcategoriaUpdate datos) {
        return productoService.actualizarSubcategoria(id, datos);
    }

    @GetMapping({"", "/"})
    public List<Map<String,Object>> listar() {
        return productoService.listarProductos(SecurityUtils.currentUser());
    }

    @GetMapping("/{idProducto}")
    public Map<String,Object> obtener(@PathVariable Integer idProducto) {
        return productoService.obtenerProducto(SecurityUtils.currentUser(), idProducto);
    }

    @PutMapping("/{idProducto}")
    public Map<String,Object> actualizar(
            @PathVariable Integer idProducto, @RequestBody ProductoDtos.ProductoUpdate datos) {
        return productoService.actualizarProducto(SecurityUtils.currentUser(), idProducto, datos);
    }

    @PutMapping("/{idProducto}/imagen")
    public Map<String,Object> imagen(
            @PathVariable Integer idProducto, @RequestParam("imagen") MultipartFile imagen) {
        return productoService.actualizarImagen(SecurityUtils.currentUser(), idProducto, imagen);
    }

    @PutMapping("/{idProducto}/con-imagen")
    public Map<String,Object> actualizarConImagen(@PathVariable Integer idProducto,
            org.springframework.web.multipart.MultipartHttpServletRequest request) {
        return productoService.actualizarConImagen(SecurityUtils.currentUser(),idProducto,
                payloads.parse(request,ProductoDtos.ProductoUpdate.class),request.getFile("imagen"));
    }

    @DeleteMapping("/{idProducto}")
    public Map<String, String> eliminar(@PathVariable Integer idProducto) {
        return productoService.eliminarProducto(SecurityUtils.currentUser(), idProducto);
    }

    @SuppressWarnings("unused")
    private BigDecimal unused() {
        return BigDecimal.ZERO;
    }
}
