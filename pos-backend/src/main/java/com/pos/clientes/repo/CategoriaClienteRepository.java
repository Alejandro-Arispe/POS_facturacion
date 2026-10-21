package com.pos.clientes.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.clientes.domain.CategoriaCliente;

public interface CategoriaClienteRepository extends JpaRepository<CategoriaCliente, Integer> {

    List<CategoriaCliente> findByIdEmpresa(Integer idEmpresa);

    Optional<CategoriaCliente> findByIdCategoriaClienteAndIdEmpresa(Integer idCategoriaCliente, Integer idEmpresa);
}
