package com.pos.productos.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;

import com.pos.common.access.EmpresaAccess;
import com.pos.common.access.RbacResolver;
import com.pos.common.ai.GeminiClient;
import com.pos.common.exception.ApiException;
import com.pos.common.storage.ProductImageStorage;
import com.pos.inventario.domain.Stock;
import com.pos.inventario.repo.StockRepository;
import com.pos.productos.domain.Producto;
import com.pos.usuarios.domain.Usuario;

/**
 * HU-27: reconocimiento de productos por camara. Envia a Gemini (modelo de vision) la foto capturada en el
 * PDV junto con las fotos de referencia de los productos con stock en la sucursal, y Gemini elige entre ellos.
 * Con confianza mayor a 80 % se agrega el producto; con menos se ofrece una lista de coincidencias y, si
 * ninguna es razonable, se informa "Producto no identificado".
 */
@Service
public class ReconocimientoProductoService {

    public static final double UMBRAL_IDENTIFICADO = 0.80;
    private static final double UMBRAL_COINCIDENCIA = 0.35;
    private static final int MAX_CANDIDATOS = 40;
    private static final int MAX_IMAGENES_REFERENCIA = 20;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp");

    private static final String INSTRUCCIONES = """
            Eres el reconocedor visual de un punto de venta de un minimarket en Bolivia.
            Recibes una lista de productos del catalogo (id, nombre y, cuando existe, su foto de referencia)
            y al final una FOTO TOMADA CON LA CAMARA del cajero. Identifica que producto del catalogo aparece en esa foto.
            Reglas:
            - Solo puedes responder ids que esten en la lista. Nunca inventes productos.
            - Compara envase, colores, marca y texto visible. La foto puede estar movida, girada o con mala luz.
            - "confianza" va de 0 a 1 y debe reflejar honestamente tu seguridad.
            - Si en la foto no hay ningun producto del catalogo, devuelve la lista vacia.
            Responde SOLO este JSON: {"coincidencias":[{"id_producto":123,"confianza":0.92,"motivo":"texto breve"}]}
            con un maximo de 3 coincidencias ordenadas de mayor a menor confianza.
            """;

    private final GeminiClient gemini;
    private final EmpresaAccess empresaAccess;
    private final RbacResolver rbac;
    private final StockRepository stocks;
    private final ProductImageStorage images;

    public ReconocimientoProductoService(GeminiClient gemini, EmpresaAccess empresaAccess, RbacResolver rbac,
            StockRepository stocks, ProductImageStorage images) {
        this.gemini = gemini;
        this.empresaAccess = empresaAccess;
        this.rbac = rbac;
        this.stocks = stocks;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reconocer(Usuario usuario, Integer idEmpresa, Integer idSucursal, MultipartFile foto) {
        if (idEmpresa == null || idSucursal == null) throw ApiException.badRequest("Indica la empresa y la sucursal.");
        empresaAccess.requirePermiso(usuario, idEmpresa, idSucursal, "sale:create");
        var grants = rbac.resolve(usuario, idEmpresa, idSucursal);
        if (!rbac.field(grants, "product", "read", "nombre"))
            throw ApiException.forbidden("Necesitas ver el nombre de los productos para reconocerlos.");
        if (foto == null || foto.isEmpty()) throw ApiException.badRequest("Captura una foto del producto.");
        String tipo = foto.getContentType() == null ? "" : foto.getContentType().toLowerCase(Locale.ROOT);
        if (!TIPOS.contains(tipo)) throw ApiException.badRequest("La foto debe ser JPG, PNG o WEBP.");
        if (foto.getSize() > MAX_BYTES) throw ApiException.badRequest("La foto supera los 5 MB.");
        if (!gemini.isEnabled())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El reconocimiento por cámara necesita la clave de Gemini (GEMINI_API_KEY) en el servidor.");

        // Candidatos: productos activos de la empresa con stock en esta sucursal.
        List<Producto> candidatos = new ArrayList<>();
        Map<Integer, Integer> stockPorProducto = new LinkedHashMap<>();
        for (Stock st : stocks.findByIdSucursal(idSucursal)) {
            Producto p = st.getProducto();
            if (p == null || !idEmpresa.equals(p.getIdEmpresa()) || !Boolean.TRUE.equals(p.getActivo())) continue;
            if (st.getCantidad() == null || st.getCantidad() <= 0) continue;
            candidatos.add(p);
            stockPorProducto.put(p.getIdProducto(), st.getCantidad());
            if (candidatos.size() >= MAX_CANDIDATOS) break;
        }
        if (candidatos.isEmpty()) throw ApiException.badRequest("No hay productos con stock en esta sucursal.");

        List<Map<String, Object>> partes = new ArrayList<>();
        partes.add(GeminiClient.texto("CATALOGO DE PRODUCTOS:"));
        int conImagen = 0;
        for (Producto p : candidatos) {
            partes.add(GeminiClient.texto("id_producto=" + p.getIdProducto() + " | " + p.getNombre()
                    + (p.getDescripcion() == null ? "" : " | " + p.getDescripcion())));
            if (conImagen < MAX_IMAGENES_REFERENCIA && p.getImagen() != null) {
                var bytes = images.read(p.getImagen());
                if (bytes.isPresent()) {
                    partes.add(GeminiClient.imagen(mimeDe(p.getImagen()), bytes.get()));
                    conImagen++;
                }
            }
        }
        byte[] contenido;
        try {
            contenido = foto.getBytes();
        } catch (java.io.IOException ex) {
            throw ApiException.badRequest("No se pudo leer la foto.");
        }
        partes.add(GeminiClient.texto("FOTO TOMADA CON LA CAMARA DEL CAJERO:"));
        partes.add(GeminiClient.imagen(tipo, contenido));

        long inicio = System.currentTimeMillis();
        JsonNode respuesta = gemini.generarJson(INSTRUCCIONES, partes)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Gemini no respondió. Intenta de nuevo o busca el producto por nombre."));
        long milisegundos = System.currentTimeMillis() - inicio;

        Map<Integer, Producto> porId = new LinkedHashMap<>();
        candidatos.forEach(p -> porId.put(p.getIdProducto(), p));
        List<Map<String, Object>> coincidencias = new ArrayList<>();
        for (JsonNode c : respuesta.path("coincidencias")) {
            Producto p = porId.get(c.path("id_producto").asInt(-1));
            double confianza = Math.max(0, Math.min(1, c.path("confianza").asDouble(0)));
            if (p == null || confianza < UMBRAL_COINCIDENCIA) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id_producto", p.getIdProducto());
            row.put("nombre", p.getNombre());
            row.put("precio", p.getPrecio());
            row.put("stock", stockPorProducto.get(p.getIdProducto()));
            row.put("imagen", p.getImagen());
            row.put("confianza", Math.round(confianza * 100) / 100.0);
            row.put("motivo", c.path("motivo").asText(""));
            coincidencias.add(row);
            if (coincidencias.size() >= 3) break;
        }
        coincidencias.sort((a, b) -> Double.compare((Double) b.get("confianza"), (Double) a.get("confianza")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelo", gemini.getModel());
        result.put("tiempo_ms", milisegundos);
        result.put("coincidencias", coincidencias);
        if (coincidencias.isEmpty()) {
            result.put("estado", "NO_IDENTIFICADO");
            result.put("mensaje", "Producto no identificado. Acerca el producto a la cámara o búscalo por nombre.");
        } else if ((Double) coincidencias.get(0).get("confianza") >= UMBRAL_IDENTIFICADO) {
            result.put("estado", "IDENTIFICADO");
            result.put("producto", coincidencias.get(0));
            result.put("mensaje", "Producto reconocido: " + coincidencias.get(0).get("nombre") + ".");
        } else {
            result.put("estado", "COINCIDENCIAS");
            result.put("mensaje", "No estoy seguro del producto. Elige la coincidencia correcta.");
        }
        return result;
    }

    private static String mimeDe(String ruta) {
        String r = ruta.toLowerCase(Locale.ROOT);
        if (r.endsWith(".png")) return "image/png";
        if (r.endsWith(".webp")) return "image/webp";
        if (r.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
