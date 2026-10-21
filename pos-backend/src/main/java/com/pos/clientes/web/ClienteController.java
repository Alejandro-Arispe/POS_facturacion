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
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping("/{idUsuario}")
    public ClienteDtos.ClienteResponse crear(
            @PathVariable Integer idUsuario, @RequestBody ClienteDtos.ClienteCreate datos) {
        return clienteService.crearCliente(SecurityUtils.currentUser(), idUsuario, datos);
    }

    @GetMapping("/{idUsuario}")
    public List<ClienteDtos.ClienteResponse> listar(@PathVariable Integer idUsuario) {
        return clienteService.listarClientes(SecurityUtils.currentUser(), idUsuario);
    }

    @GetMapping("/{idUsuario}/{idCliente}")
    public ClienteDtos.ClienteResponse obtener(
            @PathVariable Integer idUsuario, @PathVariable Integer idCliente) {
        return clienteService.obtenerCliente(SecurityUtils.currentUser(), idUsuario, idCliente);
    }

    @PutMapping("/{idUsuario}/{idCliente}")
    public ClienteDtos.ClienteResponse actualizar(
            @PathVariable Integer idUsuario,
            @PathVariable Integer idCliente,
            @RequestBody ClienteDtos.ClienteUpdate datos) {
        return clienteService.actualizarCliente(SecurityUtils.currentUser(), idUsuario, idCliente, datos);
    }
}
