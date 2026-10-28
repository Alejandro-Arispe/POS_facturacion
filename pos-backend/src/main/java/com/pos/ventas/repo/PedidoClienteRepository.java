package com.pos.ventas.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pos.ventas.domain.PedidoCliente;

public interface PedidoClienteRepository extends JpaRepository<PedidoCliente, Integer> {

    List<PedidoCliente> findByIdEmpresa(Integer idEmpresa);

    List<PedidoCliente> findByIdEmpresaOrderByFechaCreacionDesc(Integer idEmpresa);

    List<PedidoCliente> findByIdCliente(Integer idCliente);

    List<PedidoCliente> findByIdClienteOrderByFechaCreacionDesc(Integer idCliente);

    List<PedidoCliente> findByIdSucursal(Integer idSucursal);

    List<PedidoCliente> findByIdEmpresaAndIdSucursal(Integer idEmpresa, Integer idSucursal);

    List<PedidoCliente> findByIdEmpresaAndEstado(Integer idEmpresa, String estado);

    List<PedidoCliente> findByIdEmpresaAndIdSucursalAndEstado(Integer idEmpresa, Integer idSucursal, String estado);

    List<PedidoCliente> findByIdEmpresaAndIdSucursalOrderByFechaCreacionDesc(Integer idEmpresa, Integer idSucursal);

    List<PedidoCliente> findByIdEmpresaAndEstadoOrderByFechaCreacionDesc(Integer idEmpresa, String estado);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from PedidoCliente e where e.idPedido = :id")
    java.util.Optional<PedidoCliente> findForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);
}
