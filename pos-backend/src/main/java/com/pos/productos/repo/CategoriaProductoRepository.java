package com.pos.productos.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.productos.domain.CategoriaProducto;

public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Integer> {

    List<CategoriaProducto> findByIdEmpresa(Integer idEmpresa);

    List<CategoriaProducto> findByIdEmpresaOrderByNombreAsc(Integer idEmpresa);

    List<CategoriaProducto> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
