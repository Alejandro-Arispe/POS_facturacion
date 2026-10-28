package com.pos.common.access;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Reuse read checks within ONE HTTP GET only. Never cache writes, sockets or future requests. */
@Component
public class RequestReadCache {
    private static final String ATTRIBUTE = RequestReadCache.class.getName();

    @SuppressWarnings("unchecked")
    public <T> T read(String key, Supplier<T> load) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                || !"GET".equals(attributes.getRequest().getMethod())) return load.get();
        var request = attributes.getRequest();
        Map<String, Object> values = (Map<String, Object>) request.getAttribute(ATTRIBUTE);
        if (values == null) {
            values = new HashMap<>();
            request.setAttribute(ATTRIBUTE, values);
        }
        return (T) values.computeIfAbsent(key, ignored -> load.get());
    }
}
