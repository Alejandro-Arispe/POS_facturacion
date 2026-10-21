package com.pos.inventario.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.inventario.domain.Stock;
import com.pos.productos.domain.Producto;

public interface StockRepository extends JpaRepository<Stock, Integer> {

    List<Stock> findByIdSucursal(Integer idSucursal);

    List<Stock> findByProducto(Producto producto);

    List<Stock> findByProducto_IdProducto(Integer idProducto);

    Optional<Stock> findByProductoAndIdSucursal(Producto producto, Integer idSucursal);

    Optional<Stock> findByProducto_IdProductoAndIdSucursal(Integer idProducto, Integer idSucursal);
}
