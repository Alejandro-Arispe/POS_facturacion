package com.pos.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.pos.common.exception.ApiException;

@Component
public class ProductImageStorage {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final Path mediaDir;

    public ProductImageStorage(@Value("${app.media-dir}") String mediaDir) {
        this.mediaDir = Paths.get(mediaDir).toAbsolutePath().normalize();
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Debe enviar una imagen.");
        }
        String original = file.getOriginalFilename() == null ? "imagen.jpg" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1) : "jpg";
        ext = ext.toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(ext)) {
            throw ApiException.badRequest("Formato de imagen no permitido.");
        }
        try {
            Path folder = mediaDir.resolve("products");
            Files.createDirectories(folder);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = folder.resolve(filename);
            file.transferTo(target);
            return "/media/products/" + filename;
        } catch (IOException ex) {
            throw ApiException.badRequest("No se pudo guardar la imagen.");
        }
    }
}
