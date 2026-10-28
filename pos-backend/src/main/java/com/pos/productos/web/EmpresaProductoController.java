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
    private final com.pos.ventas.service.InvoiceReadService invoiceRead;
    private final ProductPayloads payloads;
    private final ClienteService clienteService;
    private final VentaService ventaService;
    private final FacturaRepository facturaRepository;

    public EmpresaProductoController(
            ProductoService productoService, com.pos.ventas.service.InvoiceReadService invoiceRead,
            ProductPayloads payloads,
            ClienteService clienteService,
            VentaService ventaService,
            FacturaRepository facturaRepository) {
        this.productoService = productoService; this.invoiceRead=invoiceRead;
        this.payloads = payloads;
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
    public List<java.util.Map<String,Object>> listarProductos(@PathVariable Integer idEmpresa) {
        return productoService.listarPorEmpresa(SecurityUtils.currentUser(), idEmpresa);
    }

    @PostMapping("/{idEmpresa}/productos")
    public java.util.Map<String,Object> crearProducto(
            @PathVariable Integer idEmpresa, @RequestBody ProductoDtos.ProductoCreate datos) {
        return productoService.crearProducto(SecurityUtils.currentUser(), idEmpresa, datos, null);
    }

    public record ProductExport(java.util.List<String> columnas,java.util.List<Integer> ids) { }

    @PostMapping("/{idEmpresa}/productos/exportar")
    public org.springframework.http.ResponseEntity<String> exportar(@PathVariable Integer idEmpresa,@RequestBody ProductExport request) {
        String csv=productoService.exportar(SecurityUtils.currentUser(),idEmpresa,request.columnas(),request.ids());
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition","attachment; filename=productos.csv")
                .contentType(new org.springframework.http.MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8)).body(csv);
    }

    @PostMapping("/{idEmpresa}/productos/con-imagen")
    public java.util.Map<String,Object> crearConImagen(@PathVariable Integer idEmpresa,
            org.springframework.web.multipart.MultipartHttpServletRequest request) {
        return productoService.crearProducto(SecurityUtils.currentUser(),idEmpresa,
                payloads.parse(request,ProductoDtos.ProductoCreate.class),request.getFile("imagen"));
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
    public List<java.util.Map<String,Object>> facturas(@PathVariable Integer idEmpresa) {
        return invoiceRead.list(idEmpresa);
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
