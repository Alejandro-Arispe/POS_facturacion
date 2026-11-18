package com.pos.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Error 400 con mensajes por campo: la respuesta lleva "detail" (resumen) y "errores" (campo → mensaje). */
public class FieldValidationException extends ApiException {

    private final Map<String, String> errores;

    public FieldValidationException(String resumen, Map<String, String> errores) {
        super(HttpStatus.BAD_REQUEST, resumen);
        this.errores = new LinkedHashMap<>(errores);
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}
