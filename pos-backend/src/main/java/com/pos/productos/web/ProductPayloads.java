package com.pos.productos.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import com.pos.common.exception.ApiException;

@Component
public class ProductPayloads {
    private final ObjectMapper mapper;
    public ProductPayloads(ObjectMapper mapper) { this.mapper=mapper; }
    public <T> T parse(MultipartHttpServletRequest request,Class<T> type) {
        Map<String,String> values=new LinkedHashMap<>();
        request.getParameterMap().forEach((key,list)->{
            if (list.length!=1 || key.equals("imagen")) throw ApiException.badRequest("Campo repetido o imagen inválida.");
            values.put(key,list[0]);
        });
        request.getMultiFileMap().forEach((key,list)->{
            if (!key.equals("imagen") || list.size()!=1) throw ApiException.badRequest("Archivo no admitido o repetido.");
        });
        if (values.containsKey("datos")) {
            if (values.size()!=1) throw ApiException.badRequest("Envía datos JSON o campos individuales, no ambos.");
            try {
                var node=mapper.readTree(values.get("datos"));
                if(!node.isObject()) throw ApiException.badRequest("Se requiere un objeto con los datos del producto.");
                return mapper.treeToValue(node,type);
            } catch (ApiException ex) { throw ex; }
            catch (RuntimeException ex) { throw ApiException.badRequest("Datos de producto inválidos."); }
        }
        return mapper.convertValue(values,type);
    }
}
