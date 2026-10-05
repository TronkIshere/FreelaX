package com.marketplace.backend.configuration;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

public final class RequestCorrelation {
    public static final String HEADER = "X-Request-Id";
    public static final String ATTRIBUTE = RequestCorrelation.class.getName() + ".id";
    private static final String MDC_KEY = "requestId";

    private RequestCorrelation() {}

    public static String id(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String id) return id;
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        return id;
    }

    public static void add(HttpHeaders headers) {
        String id = MDC.get(MDC_KEY);
        headers.set(HEADER, id == null ? UUID.randomUUID().toString() : id);
    }

    static void set(String id) { MDC.put(MDC_KEY, id); }
    static void clear() { MDC.remove(MDC_KEY); }
}
