package com.pos.inventario.repo;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.inventario.domain.MovimientoInventario;
import com.pos.productos.domain.Producto;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer> {

    List<MovimientoInventario> findByIdSucursal(Integer idSucursal);

    List<MovimientoInventario> findByProducto(Producto producto);

    List<MovimientoInventario> findByProducto_IdProducto(Integer idProducto);

    List<MovimientoInventario> findByIdSucursalAndProducto(Integer idSucursal, Producto producto);

    List<MovimientoInventario> findByIdSucursalOrderByFechaMovimientoDesc(Integer idSucursal, Pageable pageable);
}
