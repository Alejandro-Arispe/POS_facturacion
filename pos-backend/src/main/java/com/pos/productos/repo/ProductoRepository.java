package com.pos.productos.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.productos.domain.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {
    List<Producto> findByImagen(String imagen);

    List<Producto> findByIdEmpresa(Integer idEmpresa);

    List<Producto> findByIdEmpresaInOrderByNombreAsc(Collection<Integer> idEmpresas);

    List<Producto> findByIdEmpresaOrderByNombreAsc(Integer idEmpresa);

    List<Producto> findBySubcategoria_IdSubcategoria(Integer idSubcategoria);

    List<Producto> findByIdProductoIn(List<Integer> ids);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Producto e where e.idProducto = :id")
    java.util.Optional<Producto> lockById(@org.springframework.data.repository.query.Param("id") Integer id);
}
