package com.pos.respaldos;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pos.common.exception.ApiException;

@Component
public class RespaldoAlmacen {

    static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern NOMBRE_SEGURO = Pattern.compile("^[0-9\\-]+.*\\.json$");
    private static final Pattern FECHA_EN_NOMBRE = Pattern.compile("^(\\d{8}-\\d{6})");

    private final ObjectMapper mapper = new ObjectMapper();

    public String guardar(Integer idEmpresa, byte[] contenido, LocalDateTime fecha, Integer idSucursal) {
        Path dir = directorio(idEmpresa);
        try {
            Files.createDirectories(dir);
            String nombre = nombreLibre(dir, fecha, idSucursal);
            Path destino = resolverDentro(dir, nombre);
            Path temporal = dir.resolve(nombre + ".tmp").normalize();
            if (temporal.getParent() == null || !temporal.getParent().equals(dir)) {
                throw ApiException.badRequest("Nombre de archivo no permitido.");
            }
            Files.write(temporal, contenido);
            try {
                mover(temporal, destino);
            } catch (IOException ex) {
                Files.deleteIfExists(temporal);
                throw ex;
            }
            return nombre;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el respaldo.");
        }
    }

    public List<RespaldoRespuestas.Resumen> listar(Integer idEmpresa) {
        Path dir = directorio(idEmpresa);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.list(dir)) {
            List<RespaldoRespuestas.Resumen> res = new ArrayList<>();
            for (Path path : archivos.filter(Files::isRegularFile).toList()) {
                String nombre = path.getFileName().toString();
                if (!NOMBRE_SEGURO.matcher(nombre).matches()) {
                    continue;
                }
                long tamano = 0;
                try {
                    tamano = Files.size(path);
                } catch (IOException ignored) {}
                
                String alcance = "EMPRESA_COMPLETA";
                Integer idSuc = null;
                String nomSuc = null;
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    JsonNode node = mapper.readTree(bytes);
                    if (node.has("alcance")) {
                        alcance = node.get("alcance").asText("EMPRESA_COMPLETA");
                    }
                    if (node.has("idSucursal") && !node.get("idSucursal").isNull()) {
                        idSuc = node.get("idSucursal").asInt();
                    }
                    if (node.has("nombreSucursal") && !node.get("nombreSucursal").isNull()) {
                        nomSuc = node.get("nombreSucursal").asText();
                    }
                } catch (Exception ignored) {}

                res.add(new RespaldoRespuestas.Resumen(
                        nombre,
                        fechaDe(path, nombre),
                        alcance,
                        idSuc,
                        nomSuc,
                        tamano));
            }
            return res.stream()
                    .sorted(Comparator.comparing(RespaldoRespuestas.Resumen::fecha).reversed())
                    .toList();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo listar los respaldos.");
        }
    }

    public byte[] leer(Integer idEmpresa, String archivo) {
        Path destino = archivoExistente(idEmpresa, archivo);
        try {
            return Files.readAllBytes(destino);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el respaldo.");
        }
    }

    private Path archivoExistente(Integer idEmpresa, String archivo) {
        validarNombre(archivo);
        Path dir = directorio(idEmpresa);
        if (!Files.isDirectory(dir)) {
            throw ApiException.notFound("No hay respaldos para esta empresa.");
        }
        Path destino = resolverDentro(dir, archivo);
        if (!Files.isRegularFile(destino)) {
            throw ApiException.notFound("No se encontró el respaldo.");
        }
        try {
            Path realDir = dir.toRealPath();
            Path real = destino.toRealPath();
            if (!real.startsWith(realDir)) {
                throw ApiException.badRequest("Nombre de archivo no permitido.");
            }
        } catch (IOException ex) {
            throw ApiException.badRequest("Nombre de archivo no permitido.");
        }
        return destino;
    }

    private static void validarNombre(String archivo) {
        if (archivo == null || !NOMBRE_SEGURO.matcher(archivo).matches()) {
            throw ApiException.badRequest("Nombre de archivo no permitido.");
        }
    }

    private static Path resolverDentro(Path dir, String archivo) {
        validarNombre(archivo);
        Path destino = dir.resolve(archivo).normalize();
        if (destino.getParent() == null || !destino.getParent().equals(dir)) {
            throw ApiException.badRequest("Nombre de archivo no permitido.");
        }
        return destino;
    }

    private static String nombreLibre(Path dir, LocalDateTime fecha, Integer idSucursal) {
        String sufijo = (idSucursal != null) ? "-suc-" + idSucursal : "";
        String nombre = FORMATO.format(fecha) + sufijo + ".json";
        if (!Files.exists(dir.resolve(nombre))) {
            return nombre;
        }
        for (int extra = 1; extra <= 20; extra++) {
            String candidato = FORMATO.format(fecha) + sufijo + "-" + extra + ".json";
            if (!Files.exists(dir.resolve(candidato))) {
                return candidato;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo reservar un nombre de respaldo.");
    }

    private static void mover(Path temporal, Path destino) throws IOException {
        try {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static LocalDateTime fechaDe(Path archivo, String nombre) {
        Matcher matcher = FECHA_EN_NOMBRE.matcher(nombre);
        if (matcher.find()) {
            try {
                return LocalDateTime.parse(matcher.group(1), FORMATO);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(archivo).toInstant(), java.time.ZoneId.systemDefault());
        } catch (IOException ex) {
            return LocalDateTime.now().withNano(0);
        }
    }

    private static Path directorio(Integer idEmpresa) {
        return Path.of("respaldos", String.valueOf(idEmpresa)).toAbsolutePath().normalize();
    }
}
