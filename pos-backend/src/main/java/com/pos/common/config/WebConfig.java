package com.pos.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.pos.auditoria.web.BitacoraInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.media-dir}")
    private String mediaDir;

    @Value("${app.xml-dir}")
    private String xmlDir;

    private final BitacoraInterceptor bitacoraInterceptor;

    public WebConfig(BitacoraInterceptor bitacoraInterceptor) {
        this.bitacoraInterceptor = bitacoraInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(bitacoraInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path mediaPath = resolveAndCreate(mediaDir);
        Path xmlPath = resolveAndCreate(xmlDir);
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaPath.toUri().toString());
        registry.addResourceHandler("/XMLS/**")
                .addResourceLocations(xmlPath.toUri().toString());
    }

    private Path resolveAndCreate(String dir) {
        Path path = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (Exception ignored) {
        }
        return path;
    }
}
