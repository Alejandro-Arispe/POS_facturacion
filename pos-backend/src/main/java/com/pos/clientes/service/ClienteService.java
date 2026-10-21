package com.pos.clientes.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.clientes.domain.CategoriaCliente;
import com.pos.clientes.domain.Cliente;
import com.pos.clientes.dto.ClienteDtos;
import com.pos.clientes.repo.CategoriaClienteRepository;
import com.pos.clientes.repo.ClienteRepository;
import com.pos.common.access.EmpresaAccess;
import com.pos.common.exception.ApiException;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.repo.UsuarioRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;

@Service
public class ClienteService {

    private final EmpresaAccess empresaAccess;
    private final CategoriaClienteRepository categoriaRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioRolRepository usuarioRolRepository;

    public ClienteService(
            EmpresaAccess empresaAccess,
            CategoriaClienteRepository categoriaRepository,
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            UsuarioRolRepository usuarioRolRepository) {
        this.empresaAccess = empresaAccess;
        this.categoriaRepository = categoriaRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioRolRepository = usuarioRolRepository;
    }

    @Transactional
    public ClienteDtos.CategoriaResponse crearCategoria(Usuario usuario, Integer idEmpresa, ClienteDtos.CategoriaCreate datos) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        CategoriaCliente categoria = new CategoriaCliente();
        categoria.setIdEmpresa(idEmpresa);
        categoria.setNombre(datos.getNombre());
        categoria.setDescripcion(datos.getDescripcion());
        categoria.setPlazoCredito(datos.getPlazoCredito() == null ? 0 : datos.getPlazoCredito());
        categoria.setDescuentoBase(nvl(datos.getDescuentoBase()));
        categoria.setLimiteCredito(nvl(datos.getLimiteCredito()));
        categoria.setActivo(Boolean.TRUE);
        return toCategoria(categoriaRepository.save(categoria));
    }

    @Transactional(readOnly = true)
    public List<ClienteDtos.CategoriaResponse> listarCategorias(Usuario usuario, Integer idEmpresa) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        return categoriaRepository.findByIdEmpresa(idEmpresa).stream().map(this::toCategoria).toList();
    }

    @Transactional(readOnly = true)
    public ClienteDtos.CategoriaResponse obtenerCategoria(Usuario usuario, Integer idEmpresa, Integer idCategoria) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        CategoriaCliente categoria = categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> ApiException.notFound("Categoria de cliente no encontrada."));
        if (!idEmpresa.equals(categoria.getIdEmpresa())) {
            throw ApiException.unauthorized("Categoria de cliente no pertenece a esta empresa.");
        }
        return toCategoria(categoria);
    }

    @Transactional
    public ClienteDtos.CategoriaResponse actualizarCategoria(
            Usuario usuario, Integer idEmpresa, Integer idCategoria, ClienteDtos.CategoriaUpdate datos) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        CategoriaCliente categoria = categoriaRepository.findByIdCategoriaClienteAndIdEmpresa(idCategoria, idEmpresa)
                .orElseThrow(() -> ApiException.notFound("Categoria de cliente no encontrada."));
        if (datos.getNombre() != null) {
            categoria.setNombre(datos.getNombre());
        }
        if (datos.getDescripcion() != null) {
            categoria.setDescripcion(datos.getDescripcion());
        }
        if (datos.getPlazoCredito() != null) {
            categoria.setPlazoCredito(datos.getPlazoCredito());
        }
        if (datos.getDescuentoBase() != null) {
            categoria.setDescuentoBase(datos.getDescuentoBase());
        }
        if (datos.getLimiteCredito() != null) {
            categoria.setLimiteCredito(datos.getLimiteCredito());
        }
        if (datos.getActivo() != null) {
            categoria.setActivo(datos.getActivo());
        }
        return toCategoria(categoriaRepository.save(categoria));
    }

    @Transactional
    public ClienteDtos.ClienteResponse crearCliente(Usuario usuario, Integer idUsuario, ClienteDtos.ClienteCreate datos) {
        empresaAccess.requireUsuarioActivo(usuario);
        usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
        CategoriaCliente categoria = categoriaRepository.findById(datos.getIdCategoriaCliente())
                .orElseThrow(() -> ApiException.notFound("Categoria de cliente no encontrada."));
        Cliente cliente = new Cliente();
        cliente.setIdUsuario(idUsuario);
        cliente.setCategoriaCliente(categoria);
        cliente.setCodigoCliente(datos.getCodigoCliente());
        cliente.setSaldoCredito(nvl(datos.getSaldoCredito()));
        cliente.setLimiteCredito(nvl(datos.getLimiteCredito()));
        cliente.setActivo(datos.getActivo() == null || datos.getActivo());
        return toCliente(clienteRepository.save(cliente));
    }

    @Transactional(readOnly = true)
    public List<ClienteDtos.ClienteResponse> listarClientes(Usuario usuario, Integer idUsuario) {
        empresaAccess.requireUsuarioActivo(usuario);
        return clienteRepository.findByIdUsuario(idUsuario).stream().map(this::toCliente).toList();
    }

    @Transactional(readOnly = true)
    public ClienteDtos.ClienteResponse obtenerCliente(Usuario usuario, Integer idUsuario, Integer idCliente) {
        empresaAccess.requireUsuarioActivo(usuario);
        return toCliente(clienteRepository.findByIdUsuarioAndIdCliente(idUsuario, idCliente)
                .orElseThrow(() -> ApiException.notFound("Cliente no encontrado.")));
    }

    @Transactional
    public ClienteDtos.ClienteResponse actualizarCliente(
            Usuario usuario, Integer idUsuario, Integer idCliente, ClienteDtos.ClienteUpdate datos) {
        empresaAccess.requireUsuarioActivo(usuario);
        Cliente cliente = clienteRepository.findByIdUsuarioAndIdCliente(idUsuario, idCliente)
                .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));
        applyUpdate(cliente, datos);
        return toCliente(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteDtos.ClienteResponse actualizarClienteEmpresa(
            Usuario usuario, Integer idEmpresa, Integer idCliente, ClienteDtos.ClienteUpdate datos) {
        empresaAccess.requireEmpresa(usuario, idEmpresa);
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> ApiException.notFound("Cliente no encontrado."));

        boolean pertenecePorCategoria = cliente.getCategoriaCliente() != null
                && idEmpresa.equals(cliente.getCategoriaCliente().getIdEmpresa());
        boolean pertenecePorRol = usuarioRolRepository.existsByUsuario_IdUsuarioAndIdEmpresaAndActivoTrue(
                cliente.getIdUsuario(), idEmpresa);

        if (!pertenecePorCategoria && !pertenecePorRol) {
            throw ApiException.notFound("Cliente no encontrado para esta empresa.");
        }

        if (datos.getIdCategoriaCliente() != null) {
            CategoriaCliente categoria = categoriaRepository.findByIdCategoriaClienteAndIdEmpresa(
                    datos.getIdCategoriaCliente(), idEmpresa)
                    .orElseThrow(() -> ApiException.badRequest("Categoria de cliente no encontrada para esta empresa."));
            cliente.setCategoriaCliente(categoria);
        }
        if (datos.getCodigoCliente() != null) {
            cliente.setCodigoCliente(datos.getCodigoCliente());
        }
        if (datos.getSaldoCredito() != null) {
            cliente.setSaldoCredito(datos.getSaldoCredito());
        }
        if (datos.getLimiteCredito() != null) {
            cliente.setLimiteCredito(datos.getLimiteCredito());
        }
        if (datos.getActivo() != null) {
            cliente.setActivo(datos.getActivo());
        }
        return toCliente(clienteRepository.save(cliente));
    }

    private void applyUpdate(Cliente cliente, ClienteDtos.ClienteUpdate datos) {
        if (datos.getIdCategoriaCliente() != null) {
            CategoriaCliente categoria = categoriaRepository.findById(datos.getIdCategoriaCliente())
                    .orElseThrow(() -> ApiException.notFound("Categoria de cliente no encontrada."));
            cliente.setCategoriaCliente(categoria);
        }
        if (datos.getCodigoCliente() != null) {
            cliente.setCodigoCliente(datos.getCodigoCliente());
        }
        if (datos.getSaldoCredito() != null) {
            cliente.setSaldoCredito(datos.getSaldoCredito());
        }
        if (datos.getLimiteCredito() != null) {
            cliente.setLimiteCredito(datos.getLimiteCredito());
        }
        if (datos.getActivo() != null) {
            cliente.setActivo(datos.getActivo());
        }
    }

    private ClienteDtos.CategoriaResponse toCategoria(CategoriaCliente categoria) {
        ClienteDtos.CategoriaResponse dto = new ClienteDtos.CategoriaResponse();
        dto.setIdCategoriaCliente(categoria.getIdCategoriaCliente());
        dto.setIdEmpresa(categoria.getIdEmpresa());
        dto.setNombre(categoria.getNombre());
        dto.setDescripcion(categoria.getDescripcion());
        dto.setPlazoCredito(categoria.getPlazoCredito());
        dto.setDescuentoBase(categoria.getDescuentoBase());
        dto.setLimiteCredito(categoria.getLimiteCredito());
        dto.setActivo(categoria.getActivo());
        return dto;
    }

    private ClienteDtos.ClienteResponse toCliente(Cliente cliente) {
        ClienteDtos.ClienteResponse dto = new ClienteDtos.ClienteResponse();
        dto.setIdCliente(cliente.getIdCliente());
        dto.setIdUsuario(cliente.getIdUsuario());
        dto.setIdCategoriaCliente(cliente.getCategoriaCliente() == null ? null : cliente.getCategoriaCliente().getIdCategoriaCliente());
        dto.setCodigoCliente(cliente.getCodigoCliente());
        dto.setSaldoCredito(cliente.getSaldoCredito());
        dto.setLimiteCredito(cliente.getLimiteCredito());
        dto.setActivo(cliente.getActivo());
        return dto;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
