package com.pos.siat.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.pos.siat.domain.SinMotivoAnulacion;
import com.pos.siat.domain.SinProductoServicio;
import com.pos.siat.domain.SinUnidadMedida;
import com.pos.siat.repo.SinMotivoAnulacionRepository;
import com.pos.siat.repo.SinProductoServicioRepository;
import com.pos.siat.repo.SinUnidadMedidaRepository;

/** Siembra idempotente de los catálogos paramétricos del SIN usados en la simulación de homologación. */
@Component
public class SiatSeedRunner implements ApplicationRunner {

    private final SinProductoServicioRepository productos;
    private final SinUnidadMedidaRepository unidades;
    private final SinMotivoAnulacionRepository motivos;

    public SiatSeedRunner(SinProductoServicioRepository productos, SinUnidadMedidaRepository unidades,
            SinMotivoAnulacionRepository motivos) {
        this.productos = productos;
        this.unidades = unidades;
        this.motivos = motivos;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        producto("47110", 99100, "Productos alimenticios en general");
        producto("47110", 23110, "Productos de panadería y pastelería");
        producto("47110", 24410, "Bebidas no alcohólicas, aguas minerales y gaseosas");
        producto("47110", 22110, "Leche y productos lácteos");
        producto("47190", 62111, "Artículos de uso doméstico y limpieza");
        producto("96090", 85999, "Servicios generales");

        unidad(58, "UNIDAD (BIENES)");
        unidad(62, "UNIDAD (SERVICIOS)");
        unidad(22, "KILOGRAMO");
        unidad(47, "LITRO");

        motivo(1, "FACTURA MAL EMITIDA");
        motivo(2, "NOTA DE CREDITO-DEBITO MAL EMITIDA");
        motivo(3, "DATOS DE EMISION INCORRECTOS");
        motivo(4, "FACTURA O NOTA DE CREDITO-DEBITO DEVUELTA");
    }

    private void producto(String actividad, int codigo, String descripcion) {
        if (productos.existsById(codigo)) return;
        SinProductoServicio p = new SinProductoServicio();
        p.setCodigoActividad(actividad);
        p.setCodigoProductoSin(codigo);
        p.setDescripcionProductoSin(descripcion);
        productos.save(p);
    }

    private void unidad(int codigo, String descripcion) {
        if (unidades.existsById(codigo)) return;
        SinUnidadMedida u = new SinUnidadMedida();
        u.setCodigoUnidadSin(codigo);
        u.setDescripcion(descripcion);
        unidades.save(u);
    }

    private void motivo(int codigo, String descripcion) {
        if (motivos.existsById(codigo)) return;
        SinMotivoAnulacion m = new SinMotivoAnulacion();
        m.setCodigoMotivoSin(codigo);
        m.setDescripcion(descripcion);
        motivos.save(m);
    }
}
