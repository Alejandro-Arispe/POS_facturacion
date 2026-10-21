package com.pos.productos.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pos.productos.domain.SubcategoriaProducto;

public interface SubcategoriaProductoRepository extends JpaRepository<SubcategoriaProducto, Integer> {

    List<SubcategoriaProducto> findByCategoriaProducto_IdCategoriaProducto(Integer idCategoriaProducto);

    @Query("select s from SubcategoriaProducto s where s.categoriaProducto.idEmpresa = :idEmpresa order by s.nombre asc")
    List<SubcategoriaProducto> findByIdEmpresa(@Param("idEmpresa") Integer idEmpresa);
}
