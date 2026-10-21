package com.pos.reportes.qbe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuentes de datos disponibles para la consulta por ejemplo (QBE). Cada campo expone una expresion SQL
 * fija: el usuario solo elige codigos de esta lista, nunca escribe SQL, por lo que no hay inyeccion posible.
 */
public final class QbeCatalogo {

	private QbeCatalogo() {
	}

	public enum Tipo {
		TEXTO, NUMERO, FECHA, BOOLEANO
	}

	public record Campo(String codigo, String etiqueta, String expresion, Tipo tipo) {
	}

	public record Fuente(
			String codigo,
			String nombre,
			String descripcion,
			String desde,
			String filtroEmpresa,
			List<Campo> campos,
			List<String> camposPorDefecto) {

		public Campo campo(String codigo) {
			return campos.stream().filter(c -> c.codigo().equals(codigo)).findFirst().orElse(null);
		}
	}

	private static final String JOIN_VENTA_SUCURSAL = """
			venta v
			join caja_sesion cs on cs.id_caja_sesion = v.id_caja_sesion
			join caja ca on ca.id_caja = cs.id_caja
			join sucursal s on s.id_sucursal = ca.id_sucursal
			left join tipo_venta tv on tv.id_tipo_venta = v.id_tipo_venta
			left join usuario uv on uv.id_usuario = v.id_usuario
			""";

	private static final String JOIN_CATEGORIA = """
			left join subcategoria_producto sp on sp.id_subcategoria = p.id_subcategoria
			left join categoria_producto cp on cp.id_categoria_producto = sp.id_categoria_producto
			""";

	public static final Map<String, Fuente> FUENTES = new LinkedHashMap<>();

	static {
		registrar(new Fuente(
				"ventas",
				"Ventas",
				"Cada venta registrada en el POS con su sucursal, tipo y montos.",
				JOIN_VENTA_SUCURSAL,
				"s.id_empresa = :idEmpresa",
				List.of(
						new Campo("id_venta", "N° venta", "v.id_venta", Tipo.NUMERO),
						new Campo("fecha", "Fecha", "v.fecha", Tipo.FECHA),
						new Campo("sucursal", "Sucursal", "s.nombre", Tipo.TEXTO),
						new Campo("ciudad", "Ciudad", "s.ciudad", Tipo.TEXTO),
						new Campo("tipo_venta", "Tipo de venta", "tv.nombre", Tipo.TEXTO),
						new Campo("estado", "Estado", "v.estado", Tipo.TEXTO),
						new Campo("vendedor", "Vendedor", "uv.email", Tipo.TEXTO),
						new Campo("subtotal", "Subtotal", "v.subtotal", Tipo.NUMERO),
						new Campo("descuento", "Descuento", "v.descuento_total", Tipo.NUMERO),
						new Campo("total", "Total", "v.total", Tipo.NUMERO)),
				List.of("id_venta", "fecha", "sucursal", "tipo_venta", "total")));

		registrar(new Fuente(
				"productos_vendidos",
				"Productos vendidos",
				"Detalle de cada producto vendido (una fila por linea de venta).",
				"detalle_venta d join " + JOIN_VENTA_SUCURSAL.replaceFirst("venta v", "venta v on v.id_venta = d.id_venta")
						+ " join producto p on p.id_producto = d.id_producto " + JOIN_CATEGORIA,
				"s.id_empresa = :idEmpresa",
				List.of(
						new Campo("fecha", "Fecha", "v.fecha", Tipo.FECHA),
						new Campo("sucursal", "Sucursal", "s.nombre", Tipo.TEXTO),
						new Campo("producto", "Producto", "p.nombre", Tipo.TEXTO),
						new Campo("categoria", "Categoría", "cp.nombre", Tipo.TEXTO),
						new Campo("subcategoria", "Subcategoría", "sp.nombre", Tipo.TEXTO),
						new Campo("cantidad", "Cantidad", "d.cantidad", Tipo.NUMERO),
						new Campo("precio_unitario", "Precio unitario", "d.precio_unitario", Tipo.NUMERO),
						new Campo("descuento", "Descuento", "d.descuento", Tipo.NUMERO),
						new Campo("total", "Total", "d.total", Tipo.NUMERO)),
				List.of("fecha", "producto", "categoria", "cantidad", "total")));

		registrar(new Fuente(
				"productos",
				"Catálogo de productos",
				"Productos de la empresa con su categoria y precio.",
				"producto p " + JOIN_CATEGORIA,
				"p.id_empresa = :idEmpresa",
				List.of(
						new Campo("producto", "Producto", "p.nombre", Tipo.TEXTO),
						new Campo("codigo_barra", "Código de barras", "p.codigo_barra", Tipo.TEXTO),
						new Campo("categoria", "Categoría", "cp.nombre", Tipo.TEXTO),
						new Campo("subcategoria", "Subcategoría", "sp.nombre", Tipo.TEXTO),
						new Campo("unidad", "Unidad", "p.unidad_medida", Tipo.TEXTO),
						new Campo("precio", "Precio", "p.precio", Tipo.NUMERO),
						new Campo("activo", "Activo", "p.activo", Tipo.BOOLEANO)),
				List.of("producto", "categoria", "unidad", "precio")));

		registrar(new Fuente(
				"inventario",
				"Inventario por sucursal",
				"Stock actual de cada producto en cada sucursal.",
				"stock st join producto p on p.id_producto = st.id_producto "
						+ "join sucursal s on s.id_sucursal = st.id_sucursal " + JOIN_CATEGORIA,
				"p.id_empresa = :idEmpresa",
				List.of(
						new Campo("sucursal", "Sucursal", "s.nombre", Tipo.TEXTO),
						new Campo("producto", "Producto", "p.nombre", Tipo.TEXTO),
						new Campo("categoria", "Categoría", "cp.nombre", Tipo.TEXTO),
						new Campo("cantidad", "Stock", "st.cantidad", Tipo.NUMERO),
						new Campo("stock_minimo", "Stock mínimo", "st.stock_minimo", Tipo.NUMERO),
						new Campo("stock_maximo", "Stock máximo", "st.stock_maximo", Tipo.NUMERO),
						new Campo("valor", "Valor del stock", "(st.cantidad * p.precio)", Tipo.NUMERO),
						new Campo("bajo_minimo", "Bajo el mínimo",
								"(case when st.cantidad < st.stock_minimo then true else false end)", Tipo.BOOLEANO)),
				List.of("sucursal", "producto", "categoria", "cantidad", "valor")));

		registrar(new Fuente(
				"movimientos",
				"Movimientos de inventario",
				"Entradas, salidas y ajustes de stock (kardex).",
				"movimiento_inventario m join producto p on p.id_producto = m.id_producto "
						+ "join sucursal s on s.id_sucursal = m.id_sucursal "
						+ "join tipo_movimiento tm on tm.id_tipo_movimiento = m.id_tipo_movimiento "
						+ "left join usuario um on um.id_usuario = m.id_usuario " + JOIN_CATEGORIA,
				"p.id_empresa = :idEmpresa",
				List.of(
						new Campo("fecha", "Fecha", "m.fecha_movimiento", Tipo.FECHA),
						new Campo("sucursal", "Sucursal", "s.nombre", Tipo.TEXTO),
						new Campo("producto", "Producto", "p.nombre", Tipo.TEXTO),
						new Campo("categoria", "Categoría", "cp.nombre", Tipo.TEXTO),
						new Campo("tipo", "Tipo de movimiento", "tm.nombre", Tipo.TEXTO),
						new Campo("direccion", "Dirección", "tm.direccion", Tipo.TEXTO),
						new Campo("cantidad", "Cantidad", "m.cantidad", Tipo.NUMERO),
						new Campo("usuario", "Usuario", "um.email", Tipo.TEXTO),
						new Campo("observacion", "Observación", "m.observacion", Tipo.TEXTO)),
				List.of("fecha", "sucursal", "producto", "tipo", "cantidad")));

		registrar(new Fuente(
				"clientes",
				"Clientes",
				"Clientes de la empresa con su categoria y credito.",
				"cliente cl join usuario u on u.id_usuario = cl.id_usuario "
						+ "join persona pe on pe.id_persona = u.id_persona "
						+ "left join categoria_cliente cc on cc.id_categoria_cliente = cl.id_categoria_cliente",
				"exists (select 1 from usuario_rol ur join rol r on r.id_rol = ur.id_rol "
						+ "where ur.id_usuario = cl.id_usuario and ur.id_empresa = :idEmpresa "
						+ "and r.nombre = 'CLIENTE' and ur.activo = true)",
				List.of(
						new Campo("codigo", "Código", "cl.codigo_cliente", Tipo.TEXTO),
						new Campo("nombre", "Nombre", "pe.nombre_completo", Tipo.TEXTO),
						new Campo("email", "Correo", "u.email", Tipo.TEXTO),
						new Campo("telefono", "Teléfono", "pe.telefono", Tipo.TEXTO),
						new Campo("categoria", "Categoría", "cc.nombre", Tipo.TEXTO),
						new Campo("descuento_base", "Descuento base (%)", "cc.descuento_base", Tipo.NUMERO),
						new Campo("limite_credito", "Límite de crédito", "cl.limite_credito", Tipo.NUMERO),
						new Campo("saldo_credito", "Saldo de crédito", "cl.saldo_credito", Tipo.NUMERO),
						new Campo("activo", "Activo", "cl.activo", Tipo.BOOLEANO)),
				List.of("codigo", "nombre", "email", "categoria", "limite_credito")));
	}

	private static void registrar(Fuente fuente) {
		FUENTES.put(fuente.codigo(), fuente);
	}

	public static final Map<Tipo, List<String>> OPERADORES = Map.of(
			Tipo.TEXTO, List.of("igual", "distinto", "contiene", "empieza", "vacio", "no_vacio"),
			Tipo.NUMERO, List.of("igual", "distinto", "mayor", "mayor_igual", "menor", "menor_igual", "entre"),
			Tipo.FECHA, List.of("igual", "mayor_igual", "menor_igual", "entre"),
			Tipo.BOOLEANO, List.of("igual"));
}
