package com.pos.productos.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pos.clientes.dto.ClienteDtos;
import com.pos.clientes.service.ClienteService;
import com.pos.common.security.SecurityUtils;
import com.pos.common.storage.ProductImageStorage;
import com.pos.productos.dto.ProductoDtos;
import com.pos.productos.service.ProductoService;
import com.pos.ventas.domain.Factura;
import com.pos.ventas.dto.VentaDtos;
import com.pos.ventas.repo.FacturaRepository;
import com.pos.ventas.service.VentaService;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaProductoController {

    private final ProductoService productoService;
    private final ProductImageStorage imageStorage;
    private final ClienteService clienteService;
    private final VentaService ventaService;
    private final FacturaRepository facturaRepository;

    public EmpresaProductoController(
            ProductoService productoService,
            ProductImageStorage imageStorage,
            ClienteService clienteService,
            VentaService ventaService,
            FacturaRepository facturaRepository) {
        this.productoService = productoService;
        this.imageStorage = imageStorage;
        this.clienteService = clienteService;
        this.ventaService = ventaService;
        this.facturaRepository = facturaRepository;
    }

    @PostMapping("/{idEmpresa}/categorias")
    public ProductoDtos.CategoriaResponse crearCategoria(
            @PathVariable Integer idEmpresa, @RequestBody ProductoDtos.CategoriaCreate datos) {
        return productoService.crearCategoria(SecurityUtils.currentUser(), idEmpresa, datos);
    }

    @GetMapping("/{idEmpresa}/productos")
    public List<ProductoDtos.ProductoResponse> listarProductos(@PathVariable Integer idEmpresa) {
        return productoService.listarPorEmpresa(SecurityUtils.currentUser(), idEmpresa);
    }

    @PostMapping("/{idEmpresa}/productos")
    public ProductoDtos.ProductoResponse crearProducto(
            @PathVariable Integer idEmpresa, @RequestBody ProductoDtos.ProductoCreate datos) {
        return productoService.crearProducto(SecurityUtils.currentUser(), idEmpresa, datos, null);
    }

    @PostMapping("/{idEmpresa}/productos/con-imagen")
    public ProductoDtos.ProductoResponse crearConImagen(
            @PathVariable Integer idEmpresa,
            @RequestParam("id_subcategoria") Integer idSubcategoria,
            @RequestParam("nombre") String nombre,
            @RequestParam(value = "codigo_barra", required = false) String codigoBarra,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam("unidad_medida") String unidadMedida,
            @RequestParam(value = "precio", required = false) BigDecimal precio,
            @RequestParam(value = "activo", required = false, defaultValue = "true") Boolean activo,
            @RequestParam(value = "imagen", required = false) MultipartFile imagen) {
        String path = null;
        if (imagen != null && !imagen.isEmpty()) {
            path = imageStorage.save(imagen);
        }
        ProductoDtos.ProductoCreate payload = new ProductoDtos.ProductoCreate();
        payload.setIdSubcategoria(idSubcategoria);
        payload.setNombre(nombre);
        payload.setCodigoBarra(codigoBarra);
        payload.setDescripcion(descripcion);
        payload.setUnidadMedida(unidadMedida);
        payload.setPrecio(precio == null ? BigDecimal.ZERO : precio);
        payload.setActivo(activo);
        return productoService.crearProducto(SecurityUtils.currentUser(), idEmpresa, payload, path);
    }

    @GetMapping("/{idEmpresa}/subcategorias")
    public List<ProductoDtos.SubcategoriaResponse> subcategorias(@PathVariable Integer idEmpresa) {
        return productoService.listarSubcategoriasPorEmpresa(SecurityUtils.currentUser(), idEmpresa);
    }

    @GetMapping("/{idEmpresa}/categorias-con-subcategorias")
    public List<ProductoDtos.CategoriaResponse> categoriasConSub(@PathVariable Integer idEmpresa) {
        return productoService.listarCategoriasConSubcategorias(SecurityUtils.currentUser(), idEmpresa);
    }

    @GetMapping("/{idEmpresa}/facturas")
    public List<VentaDtos.FacturaResponse> facturas(@PathVariable Integer idEmpresa) {
        List<VentaDtos.FacturaResponse> result = new ArrayList<>();
        for (Factura factura : facturaRepository.findByIdEmpresa(idEmpresa)) {
            VentaDtos.FacturaResponse dto = new VentaDtos.FacturaResponse();
            dto.setIdFactura(factura.getIdFactura());
            dto.setIdVenta(factura.getVenta().getIdVenta());
            dto.setNitEmisor(factura.getNitEmisor());
            dto.setNumeroFactura(factura.getNumeroFactura());
            dto.setFechaEmision(factura.getFechaEmision());
            dto.setNitCliente(factura.getNitCliente());
            dto.setMontoTotal(factura.getMontoTotal());
            dto.setIva(factura.getIva());
            dto.setCufd(factura.getCufd());
            dto.setCuf(factura.getCuf());
            dto.setXmlGenerado(factura.getXmlGenerado());
            dto.setPdfGenerado(factura.getPdfGenerado());
            result.add(dto);
        }
        return result;
    }

    @GetMapping("/{idEmpresa}/categorias-cliente")
    public List<ClienteDtos.CategoriaResponse> categoriasCliente(@PathVariable Integer idEmpresa) {
        return clienteService.listarCategorias(SecurityUtils.currentUser(), idEmpresa);
    }

    @PutMapping("/{idEmpresa}/categorias-cliente")
    public ClienteDtos.CategoriaResponse actualizarCategoriaColeccion(
            @PathVariable Integer idEmpresa, @RequestBody ClienteDtos.CategoriaUpdate datos) {
        Integer id = datos.getIdCategoriaCliente();
        return clienteService.actualizarCategoria(SecurityUtils.currentUser(), idEmpresa, id, datos);
    }

    @PutMapping("/{idEmpresa}/categorias-cliente/{idCategoriaCliente}")
    public ClienteDtos.CategoriaResponse actualizarCategoria(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idCategoriaCliente,
            @RequestBody ClienteDtos.CategoriaUpdate datos) {
        return clienteService.actualizarCategoria(SecurityUtils.currentUser(), idEmpresa, idCategoriaCliente, datos);
    }

    @PutMapping("/{idEmpresa}/clientes/{idCliente}")
    public ClienteDtos.ClienteResponse actualizarClienteEmpresa(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idCliente,
            @RequestBody ClienteDtos.ClienteUpdate datos) {
        return clienteService.actualizarClienteEmpresa(SecurityUtils.currentUser(), idEmpresa, idCliente, datos);
    }

    @GetMapping("/{idEmpresa}/clientes/{idCliente}/cuentas-por-cobrar")
    public List<VentaDtos.CuentaPorCobrarResponse> cuentas(
            @PathVariable Integer idEmpresa, @PathVariable Integer idCliente) {
        return ventaService.cuentasPorCobrar(SecurityUtils.currentUser(), idEmpresa, idCliente);
    }
}
