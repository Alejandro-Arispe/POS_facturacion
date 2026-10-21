package com.pos.clientes.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.clientes.dto.ClienteDtos;
import com.pos.clientes.service.ClienteService;
import com.pos.common.security.SecurityUtils;

@RestController
@RequestMapping("/api/categorias-cliente")
public class CategoriaClienteController {

    private final ClienteService clienteService;

    public CategoriaClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping("/{idEmpresa}")
    public ClienteDtos.CategoriaResponse crear(
            @PathVariable Integer idEmpresa, @RequestBody ClienteDtos.CategoriaCreate datos) {
        return clienteService.crearCategoria(SecurityUtils.currentUser(), idEmpresa, datos);
    }

    @GetMapping("/{idEmpresa}")
    public List<ClienteDtos.CategoriaResponse> listar(@PathVariable Integer idEmpresa) {
        return clienteService.listarCategorias(SecurityUtils.currentUser(), idEmpresa);
    }

    @GetMapping("/{idEmpresa}/{idCategoriaCliente}")
    public ClienteDtos.CategoriaResponse obtener(
            @PathVariable Integer idEmpresa, @PathVariable Integer idCategoriaCliente) {
        return clienteService.obtenerCategoria(SecurityUtils.currentUser(), idEmpresa, idCategoriaCliente);
    }

    @PutMapping("/{idEmpresa}/{idCategoriaCliente}")
    public ClienteDtos.CategoriaResponse actualizar(
            @PathVariable Integer idEmpresa,
            @PathVariable Integer idCategoriaCliente,
            @RequestBody ClienteDtos.CategoriaUpdate datos) {
        return clienteService.actualizarCategoria(SecurityUtils.currentUser(), idEmpresa, idCategoriaCliente, datos);
    }
}
