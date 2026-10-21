package com.pos.superadmin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.clientes.domain.Cliente;
import com.pos.clientes.repo.ClienteRepository;
import com.pos.common.exception.ApiException;
import com.pos.empresas.domain.Caja;
import com.pos.empresas.domain.CajaSesion;
import com.pos.empresas.domain.MovimientoCaja;
import com.pos.empresas.domain.Sucursal;
import com.pos.empresas.domain.TipoMovimientoCaja;
import com.pos.empresas.repo.CajaRepository;
import com.pos.empresas.repo.CajaSesionRepository;
import com.pos.empresas.repo.MovimientoCajaRepository;
import com.pos.empresas.repo.SucursalRepository;
import com.pos.empresas.repo.TipoMovimientoCajaRepository;
import com.pos.inventario.domain.MovimientoInventario;
import com.pos.inventario.domain.TipoMovimiento;
import com.pos.inventario.repo.MovimientoInventarioRepository;
import com.pos.inventario.repo.TipoMovimientoRepository;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.usuarios.domain.Rol;
import com.pos.usuarios.domain.Usuario;
import com.pos.usuarios.domain.UsuarioRol;
import com.pos.usuarios.repo.RolRepository;
import com.pos.usuarios.repo.UsuarioRolRepository;
import com.pos.ventas.domain.DetalleVenta;
import com.pos.ventas.domain.MetodoPago;
import com.pos.ventas.domain.TipoVenta;
import com.pos.ventas.domain.Venta;
import com.pos.ventas.domain.VentaPago;
import com.pos.ventas.repo.DetalleVentaRepository;
import com.pos.ventas.repo.MetodoPagoRepository;
import com.pos.ventas.repo.TipoVentaRepository;
import com.pos.ventas.repo.VentaPagoRepository;
import com.pos.ventas.repo.VentaRepository;

/**
 * Genera un historial de ventas de demostracion con fechas pasadas, coherente con el resto del sistema:
 * las ventas las hacen los empleados de cada sucursal, los clientes mayoristas reciben su descuento,
 * cada venta deja su movimiento de caja y su salida en el kardex. Para no alterar el stock actual se
 * registra una entrada inicial equivalente a lo vendido al comienzo del periodo.
 * Todo queda marcado como DEMO y se puede eliminar con {@link #limpiar(Integer)}.
 */
@Service
public class DemoDataService {

	public static final String NOTA_DEMO = "DEMO";
	private static final String MARCA_INV = "[DEMO] ";

	private final SuperAdminService superAdminService;
	private final SucursalRepository sucursalRepository;
	private final CajaRepository cajaRepository;
	private final CajaSesionRepository cajaSesionRepository;
	private final ProductoRepository productoRepository;
	private final TipoVentaRepository tipoVentaRepository;
	private final MetodoPagoRepository metodoPagoRepository;
	private final VentaRepository ventaRepository;
	private final DetalleVentaRepository detalleVentaRepository;
	private final VentaPagoRepository ventaPagoRepository;
	private final MovimientoCajaRepository movimientoCajaRepository;
	private final TipoMovimientoCajaRepository tipoMovimientoCajaRepository;
	private final MovimientoInventarioRepository movimientoInventarioRepository;
	private final TipoMovimientoRepository tipoMovimientoRepository;
	private final UsuarioRolRepository usuarioRolRepository;
	private final RolRepository rolRepository;
	private final ClienteRepository clienteRepository;
	private final NamedParameterJdbcTemplate jdbc;

	public DemoDataService(
			SuperAdminService superAdminService,
			SucursalRepository sucursalRepository,
			CajaRepository cajaRepository,
			CajaSesionRepository cajaSesionRepository,
			ProductoRepository productoRepository,
			TipoVentaRepository tipoVentaRepository,
			MetodoPagoRepository metodoPagoRepository,
			VentaRepository ventaRepository,
			DetalleVentaRepository detalleVentaRepository,
			VentaPagoRepository ventaPagoRepository,
			MovimientoCajaRepository movimientoCajaRepository,
			TipoMovimientoCajaRepository tipoMovimientoCajaRepository,
			MovimientoInventarioRepository movimientoInventarioRepository,
			TipoMovimientoRepository tipoMovimientoRepository,
			UsuarioRolRepository usuarioRolRepository,
			RolRepository rolRepository,
			ClienteRepository clienteRepository,
			NamedParameterJdbcTemplate jdbc) {
		this.superAdminService = superAdminService;
		this.sucursalRepository = sucursalRepository;
		this.cajaRepository = cajaRepository;
		this.cajaSesionRepository = cajaSesionRepository;
		this.productoRepository = productoRepository;
		this.tipoVentaRepository = tipoVentaRepository;
		this.metodoPagoRepository = metodoPagoRepository;
		this.ventaRepository = ventaRepository;
		this.detalleVentaRepository = detalleVentaRepository;
		this.ventaPagoRepository = ventaPagoRepository;
		this.movimientoCajaRepository = movimientoCajaRepository;
		this.tipoMovimientoCajaRepository = tipoMovimientoCajaRepository;
		this.movimientoInventarioRepository = movimientoInventarioRepository;
		this.tipoMovimientoRepository = tipoMovimientoRepository;
		this.usuarioRolRepository = usuarioRolRepository;
		this.rolRepository = rolRepository;
		this.clienteRepository = clienteRepository;
		this.jdbc = jdbc;
	}

	@Transactional
	public Map<String, Object> generar(Integer idEmpresa, Integer dias) {
		Usuario superAdmin = superAdminService.requireSuperAdmin();
		int totalDias = dias == null || dias <= 0 ? 90 : Math.min(dias, 365);

		List<Sucursal> sucursales = sucursalRepository.findByEmpresa_IdEmpresaAndActivoTrue(idEmpresa);
		if (sucursales.isEmpty()) {
			throw ApiException.badRequest("La empresa no tiene sucursales activas.");
		}
		if (existenDatosDemo(idEmpresa)) {
			throw ApiException.conflict("Ya existen datos de demostracion.",
					"Ya existen datos de demostracion en esta empresa. Eliminalos antes de generarlos de nuevo.");
		}
		List<Producto> productos = productoRepository.findAll().stream()
				.filter(p -> idEmpresa.equals(p.getIdEmpresa()) && Boolean.TRUE.equals(p.getActivo()))
				.toList();
		if (productos.isEmpty()) {
			throw ApiException.badRequest("La empresa no tiene productos activos.");
		}
		TipoVenta contado = tipoVentaRepository.findByNombreIgnoreCase("Contado")
				.orElseThrow(() -> ApiException.badRequest("No existe el tipo de venta Contado."));
		List<MetodoPago> metodos = metodoPagoRepository.findAll();
		if (metodos.isEmpty()) {
			throw ApiException.badRequest("No hay metodos de pago configurados.");
		}
		TipoMovimientoCaja tipoIngreso = tipoMovimientoCajaRepository.findByNombreIgnoreCase("INGRESO")
				.orElseThrow(() -> ApiException.badRequest("No existe el tipo de movimiento de caja INGRESO."));
		TipoMovimiento tipoEntrada = tipoMovimientoRepository.findByNombreIgnoreCase("Entrada manual")
				.orElseThrow(() -> ApiException.badRequest("No existe el tipo de movimiento Entrada manual."));
		TipoMovimiento tipoVentaInv = tipoMovimientoRepository.findByNombreIgnoreCase("Venta")
				.orElseThrow(() -> ApiException.badRequest("No existe el tipo de movimiento Venta."));

		List<Cliente> mayoristas = clientesMayoristas(idEmpresa);
		Random random = new Random(idEmpresa * 7919L + totalDias);
		// popularidad fija por producto: algunos se venden mucho mas que otros
		double[] popularidad = new double[productos.size()];
		double suma = 0;
		for (int i = 0; i < productos.size(); i++) {
			popularidad[i] = 0.3 + random.nextDouble() * (i % 3 == 0 ? 2.5 : 1.0);
			suma += popularidad[i];
		}

		LocalDate hoy = LocalDate.now();
		LocalDate inicio = hoy.minusDays(totalDias);
		int ventasCreadas = 0;
		BigDecimal montoTotal = BigDecimal.ZERO;
		Map<Integer, Integer> vendidoPorProducto = new LinkedHashMap<>();
		List<Object[]> salidas = new ArrayList<>(); // producto, sucursal, usuario, cantidad, idVenta, fecha

		for (Sucursal sucursal : sucursales) {
			Caja caja = cajaDemo(sucursal);
			List<Integer> cajeros = cajeros(idEmpresa, sucursal.getIdSucursal(), superAdmin.getIdUsuario());
			for (int d = totalDias; d >= 1; d--) {
				LocalDate dia = hoy.minusDays(d);
				double tendencia = 1.0 + (totalDias - d) / (double) totalDias * 0.35;
				double estacionalidad = switch (dia.getDayOfWeek()) {
					case SATURDAY -> 1.6;
					case SUNDAY -> 1.3;
					case FRIDAY -> 1.25;
					case MONDAY -> 0.8;
					default -> 1.0;
				};
				int ventasDia = Math.max(2, (int) Math.round((6 + random.nextGaussian() * 1.5) * tendencia * estacionalidad));
				Integer cajero = cajeros.get(random.nextInt(cajeros.size()));

				CajaSesion sesion = new CajaSesion();
				sesion.setCaja(caja);
				sesion.setIdUsuario(cajero);
				sesion.setFechaApertura(dia.atTime(8, 0));
				sesion.setFechaCierre(dia.atTime(21, 0));
				sesion.setMontoInicial(new BigDecimal("500.00"));
				sesion.setEstado("Cerrado");
				sesion.setNota(NOTA_DEMO);
				sesion = cajaSesionRepository.save(sesion);

				BigDecimal totalSesion = BigDecimal.ZERO;
				for (int v = 0; v < ventasDia; v++) {
					LocalDateTime fecha = dia.atTime(9 + random.nextInt(11), random.nextInt(60), random.nextInt(60));
					boolean esMayorista = !mayoristas.isEmpty() && random.nextDouble() < 0.22;
					Cliente cliente = esMayorista ? mayoristas.get(random.nextInt(mayoristas.size())) : null;
					BigDecimal descuentoPct = esMayorista && cliente.getCategoriaCliente() != null
							&& cliente.getCategoriaCliente().getDescuentoBase() != null
									? cliente.getCategoriaCliente().getDescuentoBase()
									: BigDecimal.ZERO;

					Map<Producto, Integer> carrito = new LinkedHashMap<>();
					int lineas = esMayorista ? 3 + random.nextInt(4) : 1 + random.nextInt(4);
					for (int l = 0; l < lineas; l++) {
						Producto producto = elegir(productos, popularidad, suma, random);
						int cantidad = esMayorista
								? 6 + random.nextInt(18)
								: 1 + random.nextInt(dia.getDayOfWeek() == DayOfWeek.SATURDAY ? 6 : 4);
						carrito.merge(producto, cantidad, Integer::sum);
					}

					BigDecimal subtotal = BigDecimal.ZERO;
					BigDecimal descuentoTotal = BigDecimal.ZERO;
					List<DetalleVenta> detalles = new ArrayList<>();
					for (Map.Entry<Producto, Integer> item : carrito.entrySet()) {
						BigDecimal precio = item.getKey().getPrecio() == null ? BigDecimal.ONE : item.getKey().getPrecio();
						BigDecimal bruto = precio.multiply(BigDecimal.valueOf(item.getValue())).setScale(2, RoundingMode.HALF_UP);
						BigDecimal descuento = bruto.multiply(descuentoPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
						BigDecimal neto = bruto.subtract(descuento);
						DetalleVenta detalle = new DetalleVenta();
						detalle.setIdProducto(item.getKey().getIdProducto());
						detalle.setCantidad(item.getValue());
						detalle.setPrecioUnitario(precio);
						detalle.setDescuento(descuento);
						detalle.setSubtotal(neto);
						detalle.setTotal(neto);
						detalle.setDescripcion(item.getKey().getNombre());
						detalles.add(detalle);
						subtotal = subtotal.add(bruto);
						descuentoTotal = descuentoTotal.add(descuento);
					}
					BigDecimal total = subtotal.subtract(descuentoTotal);

					Venta venta = new Venta();
					venta.setTipoVenta(contado);
					venta.setIdCliente(cliente == null ? null : cliente.getIdCliente());
					venta.setIdCajaSesion(sesion.getIdCajaSesion());
					venta.setIdUsuario(cajero);
					venta.setSubtotal(subtotal);
					venta.setDescuentoTotal(descuentoTotal);
					venta.setTotal(total);
					venta.setFecha(fecha);
					venta.setEstado("PAGADA");
					venta = ventaRepository.save(venta);
					for (DetalleVenta detalle : detalles) {
						detalle.setVenta(venta);
						detalleVentaRepository.save(detalle);
						vendidoPorProducto.merge(detalle.getIdProducto(), detalle.getCantidad(), Integer::sum);
						salidas.add(new Object[] {detalle.getIdProducto(), sucursal.getIdSucursal(), cajero,
								detalle.getCantidad(), venta.getIdVenta(), fecha});
					}

					// los mayoristas pagan mas seguido con transferencia/QR; el publico general en efectivo
					MetodoPago metodo = metodos.get(esMayorista
							? random.nextInt(metodos.size())
							: (random.nextDouble() < 0.6 ? 0 : random.nextInt(metodos.size())));
					VentaPago pago = new VentaPago();
					pago.setIdVenta(venta.getIdVenta());
					pago.setIdMetodoPago(metodo.getIdMetodoPago());
					pago.setVenta(venta);
					pago.setMonto(total);
					pago.setFecha(fecha);
					ventaPagoRepository.save(pago);

					MovimientoCaja movimiento = new MovimientoCaja();
					movimiento.setIdMetodoPago(metodo.getIdMetodoPago());
					movimiento.setTipoMovimientoCaja(tipoIngreso);
					movimiento.setCajaSesion(sesion);
					movimiento.setIdUsuario(cajero);
					movimiento.setFecha(fecha);
					movimiento.setMonto(total);
					movimiento.setConcepto("Venta " + venta.getIdVenta());
					movimientoCajaRepository.save(movimiento);

					totalSesion = totalSesion.add(total);
					montoTotal = montoTotal.add(total);
					ventasCreadas++;
				}
				sesion.setMontoFinal(sesion.getMontoInicial().add(totalSesion));
				cajaSesionRepository.save(sesion);
			}
		}

		// Kardex: una entrada inicial por producto y sucursal por lo que se vendera, y la salida de cada linea.
		Sucursal primera = sucursales.get(0);
		Map<String, Integer> entradas = new LinkedHashMap<>();
		for (Object[] s : salidas) {
			entradas.merge(s[0] + ":" + s[1], (Integer) s[3], Integer::sum);
		}
		Map<Integer, Producto> porId = new LinkedHashMap<>();
		productos.forEach(p -> porId.put(p.getIdProducto(), p));
		for (Map.Entry<String, Integer> e : entradas.entrySet()) {
			String[] partes = e.getKey().split(":");
			registrarMovimiento(porId.get(Integer.valueOf(partes[0])), tipoEntrada, superAdmin.getIdUsuario(),
					Integer.valueOf(partes[1]), e.getValue(), "Reposicion inicial del periodo", inicio.atTime(7, 0));
		}
		for (Object[] s : salidas) {
			registrarMovimiento(porId.get((Integer) s[0]), tipoVentaInv, (Integer) s[2], (Integer) s[1],
					(Integer) s[3], "Movimiento de la venta " + s[4], (LocalDateTime) s[5]);
		}

		Map<String, Object> resumen = new LinkedHashMap<>();
		resumen.put("dias", totalDias);
		resumen.put("sucursales", sucursales.size());
		resumen.put("ventas_creadas", ventasCreadas);
		resumen.put("monto_total", montoTotal);
		resumen.put("movimientos_inventario", entradas.size() + salidas.size());
		resumen.put("stock_actual_modificado", false);
		resumen.put("sucursal_de_referencia", primera.getNombre());
		return resumen;
	}

	/** Elimina todo lo generado por {@link #generar(Integer, Integer)} para la empresa. */
	@Transactional
	public Map<String, Object> limpiar(Integer idEmpresa) {
		superAdminService.requireSuperAdmin();
		MapSqlParameterSource p = new MapSqlParameterSource("idEmpresa", idEmpresa);
		String sesionesDemo = "select cs.id_caja_sesion from caja_sesion cs join caja ca on ca.id_caja = cs.id_caja "
				+ "join sucursal s on s.id_sucursal = ca.id_sucursal where s.id_empresa = :idEmpresa and cs.nota = 'DEMO'";
		String ventasDemo = "select v.id_venta from venta v where v.id_caja_sesion in (" + sesionesDemo + ")";
		int pagos = jdbc.update("delete from venta_pago where id_venta in (" + ventasDemo + ")", p);
		jdbc.update("delete from detalle_venta where id_venta in (" + ventasDemo + ")", p);
		int ventas = jdbc.update("delete from venta where id_caja_sesion in (" + sesionesDemo + ")", p);
		jdbc.update("delete from movimiento_caja where id_caja_sesion in (" + sesionesDemo + ")", p);
		int movInv = jdbc.update("delete from movimiento_inventario where observacion = 'Reposicion inicial del periodo' "
				+ "or id_movimiento_inventario in (select mi.id_movimiento_inventario from movimiento_inventario mi "
				+ "join producto pr on pr.id_producto = mi.id_producto where pr.id_empresa = :idEmpresa "
				+ "and mi.observacion like 'Movimiento de la venta %' "
				+ "and cast(substring(mi.observacion, 24) as integer) not in (select id_venta from venta))", p);
		jdbc.update("delete from caja_sesion where id_caja_sesion in (" + sesionesDemo + ")", p);
		jdbc.update("delete from caja where codigo = 'DEMO' and id_sucursal in "
				+ "(select id_sucursal from sucursal where id_empresa = :idEmpresa)", p);
		Map<String, Object> resumen = new LinkedHashMap<>();
		resumen.put("ventas_eliminadas", ventas);
		resumen.put("pagos_eliminados", pagos);
		resumen.put("movimientos_inventario_eliminados", movInv);
		return resumen;
	}

	private boolean existenDatosDemo(Integer idEmpresa) {
		Integer cantidad = jdbc.queryForObject(
				"select count(*) from caja_sesion cs join caja ca on ca.id_caja = cs.id_caja "
						+ "join sucursal s on s.id_sucursal = ca.id_sucursal "
						+ "where s.id_empresa = :idEmpresa and cs.nota = 'DEMO'",
				new MapSqlParameterSource("idEmpresa", idEmpresa), Integer.class);
		return cantidad != null && cantidad > 0;
	}

	private void registrarMovimiento(
			Producto producto, TipoMovimiento tipo, Integer idUsuario, Integer idSucursal,
			Integer cantidad, String observacion, LocalDateTime fecha) {
		MovimientoInventario m = new MovimientoInventario();
		m.setProducto(producto);
		m.setTipoMovimiento(tipo);
		m.setIdUsuario(idUsuario);
		m.setIdSucursal(idSucursal);
		m.setCantidad(cantidad);
		m.setObservacion(observacion);
		m.setFechaMovimiento(fecha);
		movimientoInventarioRepository.save(m);
	}

	/** Empleados activos de la sucursal; si no hay, la venta se atribuye al SuperAdmin que genera los datos. */
	private List<Integer> cajeros(Integer idEmpresa, Integer idSucursal, Integer idPorDefecto) {
		List<Integer> ids = new ArrayList<>();
		Rol empleado = rolRepository.findByNombreIgnoreCase("EMPLEADO").orElse(null);
		if (empleado != null) {
			for (UsuarioRol ur : usuarioRolRepository.findByIdEmpresaAndRol_IdRolAndActivoTrue(idEmpresa, empleado.getIdRol())) {
				if (idSucursal.equals(ur.getIdSucursal()) && !ids.contains(ur.getUsuario().getIdUsuario())) {
					ids.add(ur.getUsuario().getIdUsuario());
				}
			}
		}
		if (ids.isEmpty()) {
			ids.add(idPorDefecto);
		}
		return ids;
	}

	private List<Cliente> clientesMayoristas(Integer idEmpresa) {
		List<Cliente> clientes = new ArrayList<>();
		Rol rolCliente = rolRepository.findByNombreIgnoreCase("CLIENTE").orElse(null);
		if (rolCliente == null) {
			return clientes;
		}
		for (UsuarioRol ur : usuarioRolRepository.findByIdEmpresaAndRol_IdRolAndActivoTrue(idEmpresa, rolCliente.getIdRol())) {
			clienteRepository.findFirstByIdUsuarioOrderByIdClienteAsc(ur.getUsuario().getIdUsuario())
					.filter(c -> c.getCategoriaCliente() != null)
					.ifPresent(clientes::add);
		}
		return clientes;
	}

	private Caja cajaDemo(Sucursal sucursal) {
		return cajaRepository.findBySucursal_IdSucursal(sucursal.getIdSucursal()).stream()
				.filter(c -> "DEMO".equals(c.getCodigo()))
				.findFirst()
				.orElseGet(() -> {
					Caja caja = new Caja();
					caja.setSucursal(sucursal);
					caja.setNombre("Caja historica");
					caja.setCodigo("DEMO");
					caja.setFechaCreacion(LocalDate.now().minusYears(1));
					caja.setActivo(Boolean.FALSE);
					return cajaRepository.save(caja);
				});
	}

	private static Producto elegir(List<Producto> productos, double[] pesos, double suma, Random random) {
		double r = random.nextDouble() * suma;
		for (int i = 0; i < productos.size(); i++) {
			r -= pesos[i];
			if (r <= 0) {
				return productos.get(i);
			}
		}
		return productos.get(productos.size() - 1);
	}
}
