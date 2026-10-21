package com.pos.inventario.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.repo.StockRepository;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;

@Service
public class InventarioStockSync {

	private final SucursalRepository sucursalRepository;
	private final ProductoRepository productoRepository;
	private final StockRepository stockRepository;

	public InventarioStockSync(
			SucursalRepository sucursalRepository,
			ProductoRepository productoRepository,
			StockRepository stockRepository) {
		this.sucursalRepository = sucursalRepository;
		this.productoRepository = productoRepository;
		this.stockRepository = stockRepository;
	}

	@Transactional
	public void sincronizarStocksPorSucursal(Integer idSucursal, LocalDateTime fechaActualizacion) {
		// TODO: mover a InventarioService cuando exista el modulo completo
		Sucursal sucursal = sucursalRepository.findById(idSucursal).orElse(null);
		if (sucursal == null || sucursal.getEmpresa() == null) {
			return;
		}
		LocalDateTime fecha = fechaActualizacion != null ? fechaActualizacion : LocalDateTime.now();
		List<Producto> productos = productoRepository.findByIdEmpresa(sucursal.getEmpresa().getIdEmpresa());
		for (Producto producto : productos) {
			boolean existe = stockRepository
					.findByProducto_IdProductoAndIdSucursal(producto.getIdProducto(), idSucursal)
					.isPresent();
			if (existe) {
				continue;
			}
			Stock stock = new Stock();
			stock.setProducto(producto);
			stock.setIdSucursal(idSucursal);
			stock.setCantidad(0);
			stock.setStockMinimo(0);
			stock.setStockMaximo(0);
			stock.setFechaActualizacion(fecha);
			stockRepository.save(stock);
		}
	}
}
