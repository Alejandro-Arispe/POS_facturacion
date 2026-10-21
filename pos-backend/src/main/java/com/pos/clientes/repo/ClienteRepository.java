package com.pos.clientes.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.clientes.domain.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    List<Cliente> findByIdUsuario(Integer idUsuario);

    Optional<Cliente> findFirstByIdUsuarioOrderByIdClienteAsc(Integer idUsuario);

    Optional<Cliente> findByIdUsuarioAndIdCliente(Integer idUsuario, Integer idCliente);

    @Query("select c from Cliente c where c.categoriaCliente.idEmpresa = :idEmpresa")
    List<Cliente> findByIdEmpresa(@Param("idEmpresa") Integer idEmpresa);

    Optional<Cliente> findFirstByIdUsuario(Integer idUsuario);

    boolean existsByCodigoCliente(String codigoCliente);
}
