package com.pos.common.access;

import java.util.List;
import java.util.Map;

/** Public API field names. IDs are routing metadata; the remaining fields are protected. */
public final class ProductFields {
    private ProductFields() { }
    public static final List<String> ALL = List.of("nombre", "descripcion", "codigo_barra", "id_subcategoria",
            "unidad_medida", "precio", "costo", "imagen", "activo");
    public static final List<String> REQUIRED_CREATE = List.of("nombre", "id_subcategoria", "unidad_medida", "precio");
    public static final Map<String,String> LABELS = Map.of("nombre", "Nombre", "descripcion", "Descripción",
            "codigo_barra", "Código de barras", "id_subcategoria", "Subcategoría", "unidad_medida", "Unidad de medida",
            "precio", "Precio de venta", "costo", "Costo de compra", "imagen", "Imagen", "activo", "Estado");
}
