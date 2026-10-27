package com.pos.ventas.service;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import com.pos.common.access.RbacResolver;
import com.pos.common.exception.ApiException;
import com.pos.productos.domain.Producto;
import com.pos.productos.repo.ProductoRepository;
import com.pos.ventas.dto.VentaDtos;

/** Validates all commercial inputs before persisting a sale or moving stock. */
@Component
public class SalePricing {
    public record Line(Producto product, int quantity, BigDecimal price, BigDecimal discount,
                       BigDecimal gross, BigDecimal net, String description) {}
    public record Quote(List<Line> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}
    private final ProductoRepository products;
    public SalePricing(ProductoRepository products) { this.products = products; }

    public Quote calculate(Integer company, VentaDtos.VentaCreate input, RbacResolver.Grants grants) {
        if (input.getDetalles() == null || input.getDetalles().isEmpty() || input.getDetalles().size() > 500)
            throw ApiException.badRequest("La venta debe contener entre 1 y 500 detalles.");
        List<Line> lines = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO, lineDiscount = BigDecimal.ZERO;
        for (var item : input.getDetalles()) {
            if (item == null || item.getIdProducto() == null || item.getCantidad() == null || item.getCantidad() <= 0)
                throw ApiException.badRequest("Producto y cantidad deben ser válidos.");
            if (!ids.add(item.getIdProducto())) throw ApiException.badRequest("Agrupa las cantidades de cada producto en un solo detalle.");
            Producto product = products.findById(item.getIdProducto()).orElseThrow(() -> ApiException.notFound("Producto no encontrado."));
            if (!company.equals(product.getIdEmpresa())) throw ApiException.notFound("Producto no encontrado en esta empresa.");
            if (!Boolean.TRUE.equals(product.getActivo())) throw ApiException.badRequest("El producto está inactivo.");
            BigDecimal catalog = money(product.getPrecio(), "precio de catálogo");
            BigDecimal price = item.getPrecioUnitario() == null ? catalog : money(item.getPrecioUnitario(), "precio unitario");
            if (price.compareTo(catalog) != 0 && !grants.allows("sale:create:precio_unitario"))
                throw ApiException.forbidden("No puedes cambiar el precio unitario de una venta.");
            BigDecimal gross = money(price.multiply(BigDecimal.valueOf(item.getCantidad())), "importe del detalle");
            BigDecimal discount = money(item.getDescuento() == null ? BigDecimal.ZERO : item.getDescuento(), "descuento");
            if (discount.signum() > 0) requireDiscount(grants);
            if (discount.compareTo(gross) > 0) throw ApiException.badRequest("El descuento no puede superar el importe del detalle.");
            BigDecimal net = gross.subtract(discount);
            sameIfSupplied(item.getSubtotal(), net, "subtotal del detalle");
            if (item.getDescripcion() != null && item.getDescripcion().length() > 1000)
                throw ApiException.badRequest("La descripción del detalle es demasiado larga.");
            lines.add(new Line(product, item.getCantidad(), price, discount, gross, net, item.getDescripcion()));
            subtotal = subtotal.add(gross); lineDiscount = lineDiscount.add(discount);
        }
        BigDecimal discount = input.getDescuentoTotal() == null ? lineDiscount : money(input.getDescuentoTotal(), "descuento total");
        if (discount.signum() > 0) requireDiscount(grants);
        if (discount.compareTo(lineDiscount) < 0 || discount.compareTo(subtotal) > 0)
            throw ApiException.badRequest("El descuento total debe incluir los descuentos de detalle y no superar el subtotal.");
        money(subtotal, "subtotal");
        BigDecimal total = subtotal.subtract(discount);
        sameIfSupplied(input.getSubtotal(), subtotal, "subtotal");
        sameIfSupplied(input.getTotal(), total, "total");
        return new Quote(List.copyOf(lines), subtotal, discount, total);
    }
    private void requireDiscount(RbacResolver.Grants grants) {
        if (!grants.allows("sale:discount") || !grants.allows("sale:create:descuento"))
            throw ApiException.forbidden("No tienes permiso para aplicar descuentos.");
    }
    public static BigDecimal money(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw ApiException.badRequest("Importe inválido para " + label + ": usa un valor no negativo con hasta dos decimales.");
        return value.setScale(2);
    }
    private void sameIfSupplied(BigDecimal supplied, BigDecimal computed, String label) {
        if (supplied != null && money(supplied, label).compareTo(computed) != 0)
            throw ApiException.badRequest("El " + label + " no coincide con el cálculo del servidor. Revisa precios y descuentos.");
    }
}
