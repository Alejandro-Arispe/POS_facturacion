package com.pos.productos.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.productos.domain.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByIdEmpresa(Integer idEmpresa);

    List<Producto> findByIdEmpresaOrderByNombreAsc(Integer idEmpresa);

    List<Producto> findBySubcategoria_IdSubcategoria(Integer idSubcategoria);

    List<Producto> findByIdProductoIn(List<Integer> ids);
}
