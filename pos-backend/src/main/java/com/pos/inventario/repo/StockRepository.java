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

    /** Productos de la empresa con stock mayor a cero en alguna sucursal. */
    @org.springframework.data.jpa.repository.Query("select distinct s.producto.idProducto from Stock s where s.producto.idEmpresa = :idEmpresa and s.cantidad > 0")
    java.util.Set<Integer> findIdsProductoConStock(@org.springframework.data.repository.query.Param("idEmpresa") Integer idEmpresa);

    /** Pares [id_sucursal, id_producto] con stock mayor a cero en la empresa. */
    @org.springframework.data.jpa.repository.Query("select s.idSucursal, s.producto.idProducto from Stock s where s.producto.idEmpresa = :idEmpresa and s.cantidad > 0")
    List<Object[]> findSucursalProductoConStock(@org.springframework.data.repository.query.Param("idEmpresa") Integer idEmpresa);
}
