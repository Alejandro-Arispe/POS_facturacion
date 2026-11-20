package com.pos.respaldos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.pos.common.exception.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Convierte un respaldo JSON en un libro de Excel de solo lectura: una hoja de resumen y una hoja por tema.
 * Funciona con cualquier version del respaldo (los antiguos simplemente no tienen hojas de ventas).
 */
@Component
public class RespaldoExcel {

    /** Clave JSON -> titulo de la hoja, en el orden en que aparecen (las ventas primero). */
    private static final Map<String, String> HOJAS_VENTAS = new LinkedHashMap<>();
    private static final Map<String, String> HOJAS_CATALOGO = new LinkedHashMap<>();

    static {
        HOJAS_VENTAS.put("ventas", "Ventas");
        HOJAS_VENTAS.put("detalles", "Detalle de ventas");
        HOJAS_VENTAS.put("pagos", "Pagos");
        HOJAS_VENTAS.put("facturas", "Facturas");
        HOJAS_VENTAS.put("sesiones_caja", "Sesiones de caja");
        HOJAS_VENTAS.put("cierres_caja", "Cierres de caja");
        HOJAS_CATALOGO.put("productos", "Productos");
        HOJAS_CATALOGO.put("categorias", "Categorias");
        HOJAS_CATALOGO.put("subcategorias", "Subcategorias");
        HOJAS_CATALOGO.put("sucursales", "Sucursales");
        HOJAS_CATALOGO.put("roles", "Roles");
        HOJAS_CATALOGO.put("rol_permisos", "Permisos de roles");
        HOJAS_CATALOGO.put("historial_suscripcion", "Suscripcion");
    }

    private static final Set<String> MONEDA = Set.of("total", "subtotal", "descuento", "descuento_total", "monto",
            "monto_total", "monto_inicial", "monto_final", "monto_esperado", "monto_real", "diferencia", "iva",
            "precio", "precio_unitario", "costo", "total_vendido", "limite_credito");

    private final ObjectMapper mapper = new ObjectMapper();

    public byte[] generar(byte[] json, String archivo) {
        JsonNode doc;
        try {
            doc = mapper.readTree(json);
        } catch (Exception ex) {
            throw ApiException.badRequest("El respaldo no tiene un JSON valido.");
        }
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            JsonNode ventas = doc.get("ventas_historial");
            resumen(libro, estilos, doc, ventas, archivo);
            if (ventas != null && !ventas.isNull()) {
                HOJAS_VENTAS.forEach((clave, titulo) -> tabla(libro, estilos, titulo, ventas.get(clave)));
            }
            HOJAS_CATALOGO.forEach((clave, titulo) -> tabla(libro, estilos, titulo, doc.get(clave)));
            claveValor(libro, estilos, "Empresa", doc.get("empresa"));
            claveValor(libro, estilos, "Configuracion", doc.get("configuracion_sistema"));
            libro.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el Excel del respaldo.");
        }
    }

    private void resumen(XSSFWorkbook libro, Estilos e, JsonNode doc, JsonNode ventas, String archivo) {
        Sheet hoja = libro.createSheet("Resumen");
        Row titulo = hoja.createRow(0);
        Cell c = titulo.createCell(0);
        c.setCellValue("Copia de seguridad - " + texto(doc.path("empresa").get("nombre")));
        c.setCellStyle(e.titulo);
        hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        List<Object[]> filas = new ArrayList<>();
        filas.add(new Object[] {"Archivo", archivo});
        filas.add(new Object[] {"Fecha del respaldo", fecha(texto(doc.get("fecha")))});
        filas.add(new Object[] {"Alcance", "SUCURSAL".equals(texto(doc.get("alcance")))
                ? "Sucursal: " + texto(doc.get("nombre_sucursal")) : "Empresa completa"});
        filas.add(new Object[] {"NIT", texto(doc.path("empresa").get("nit"))});
        filas.add(new Object[] {"Productos", tamano(doc.get("productos"))});
        filas.add(new Object[] {"Sucursales", tamano(doc.get("sucursales"))});
        if (ventas != null && !ventas.isNull()) {
            filas.add(new Object[] {"Ventas desde", texto(ventas.get("desde"))});
            filas.add(new Object[] {"Ventas hasta", texto(ventas.get("hasta"))});
            filas.add(new Object[] {"Cantidad de ventas", ventas.path("cantidad_ventas").asInt()});
            filas.add(new Object[] {"Total vendido (Bs, sin anuladas)", ventas.path("total_vendido").asDouble()});
            filas.add(new Object[] {"Facturas", tamano(ventas.get("facturas"))});
        } else {
            filas.add(new Object[] {"Ventas", "Este respaldo es anterior a la inclusion de ventas."});
        }
        filas.add(new Object[] {"Nota", texto(doc.get("mensaje"))});
        int r = 2;
        for (Object[] fila : filas) {
            Row row = hoja.createRow(r++);
            Cell k = row.createCell(0);
            k.setCellValue((String) fila[0]);
            k.setCellStyle(e.encabezado);
            Cell v = row.createCell(1);
            if (fila[1] instanceof Number n) {
                v.setCellValue(n.doubleValue());
                v.setCellStyle(String.valueOf(fila[0]).startsWith("Total") ? e.moneda : e.normal);
            } else {
                v.setCellValue(String.valueOf(fila[1]));
                v.setCellStyle(e.texto);
            }
        }
        hoja.setColumnWidth(0, 34 * 256);
        hoja.setColumnWidth(1, 90 * 256);
    }

    /** Arreglo de objetos -> tabla con encabezados (union de todas las claves, en orden de aparicion). */
    private void tabla(XSSFWorkbook libro, Estilos e, String titulo, JsonNode arreglo) {
        if (arreglo == null || !arreglo.isArray() || arreglo.isEmpty()) {
            return;
        }
        Set<String> columnas = new LinkedHashSet<>();
        for (JsonNode fila : arreglo) {
            fila.propertyNames().forEach(columnas::add);
        }
        Sheet hoja = libro.createSheet(titulo);
        Row cab = hoja.createRow(0);
        int i = 0;
        for (String col : columnas) {
            Cell c = cab.createCell(i++);
            c.setCellValue(humano(col));
            c.setCellStyle(e.encabezado);
        }
        int r = 1;
        for (JsonNode fila : arreglo) {
            Row row = hoja.createRow(r++);
            int j = 0;
            for (String col : columnas) {
                escribir(row.createCell(j++), fila.get(col), col, e);
            }
        }
        hoja.createFreezePane(0, 1);
        hoja.setAutoFilter(new CellRangeAddress(0, r - 1, 0, columnas.size() - 1));
        ajustar(hoja, columnas.size());
    }

    private void claveValor(XSSFWorkbook libro, Estilos e, String titulo, JsonNode objeto) {
        if (objeto == null || !objeto.isObject()) {
            return;
        }
        Sheet hoja = libro.createSheet(titulo);
        int r = 0;
        for (String clave : objeto.propertyNames()) {
            Row row = hoja.createRow(r++);
            Cell k = row.createCell(0);
            k.setCellValue(humano(clave));
            k.setCellStyle(e.encabezado);
            escribir(row.createCell(1), objeto.get(clave), clave, e);
        }
        ajustar(hoja, 2);
    }

    private void escribir(Cell c, JsonNode v, String columna, Estilos e) {
        if (v == null || v.isNull()) {
            c.setCellStyle(e.normal);
            return;
        }
        if (v.isNumber()) {
            c.setCellValue(v.asDouble());
            c.setCellStyle(MONEDA.contains(columna) ? e.moneda : e.normal);
        } else if (v.isBoolean()) {
            c.setCellValue(v.asBoolean() ? "Si" : "No");
            c.setCellStyle(e.normal);
        } else if (v.isArray() || v.isObject()) {
            c.setCellValue(v.toString());
            c.setCellStyle(e.texto);
        } else {
            c.setCellValue(fecha(v.asText()));
            c.setCellStyle(e.texto);
        }
    }

    private static void ajustar(Sheet hoja, int columnas) {
        for (int i = 0; i < columnas; i++) {
            hoja.autoSizeColumn(i);
            int ancho = Math.min(Math.max(hoja.getColumnWidth(i) + 512, 10 * 256), 60 * 256);
            hoja.setColumnWidth(i, ancho);
        }
    }

    /** "2026-11-20T12:18:21" -> "2026-11-20 12:18:21". */
    private static String fecha(String s) {
        return s != null && s.length() >= 16 && s.charAt(10) == 'T' && Character.isDigit(s.charAt(0)) ? s.replace('T', ' ') : s;
    }

    /** "id_metodo_pago" -> "Id metodo pago". */
    private static String humano(String clave) {
        String s = clave.replace('_', ' ').trim();
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String texto(JsonNode n) {
        return n == null || n.isNull() ? "" : n.asText();
    }

    private static int tamano(JsonNode n) {
        return n != null && n.isArray() ? n.size() : 0;
    }

    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle encabezado;
        final CellStyle normal;
        final CellStyle texto;
        final CellStyle moneda;

        Estilos(XSSFWorkbook libro) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            Font grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(grande);
            encabezado = libro.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordes(encabezado);
            normal = libro.createCellStyle();
            bordes(normal);
            texto = libro.createCellStyle();
            bordes(texto);
            texto.setWrapText(false);
            moneda = libro.createCellStyle();
            bordes(moneda);
            moneda.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
        }

        private static void bordes(CellStyle s) {
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
