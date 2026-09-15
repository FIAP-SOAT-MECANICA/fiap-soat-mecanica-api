package br.com.fiap.soat.mecanica.config.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String UUID_PATH_SEGMENT =
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)";
    private static final String NUMERIC_PATH_SEGMENT = "(?<=/)\\d+(?=/|$)";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = validRequestId(request.getHeader(REQUEST_ID_HEADER));
        long start = System.nanoTime();

        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                if (!isHealthCheck(request)) {
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    log.atInfo()
                            .addKeyValue("event", "http_request_completed")
                            .addKeyValue("method", request.getMethod())
                            .addKeyValue("route", normalizedRoute(request))
                            .addKeyValue("status", response.getStatus())
                            .addKeyValue("durationMs", durationMs)
                            .log("HTTP request completed");
                }
            }
        }
    }

    String normalizedRoute(HttpServletRequest request) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (routePattern != null) {
            return routePattern.toString();
        }

        return request.getRequestURI()
                .replaceAll(UUID_PATH_SEGMENT, "{id}")
                .replaceAll(NUMERIC_PATH_SEGMENT, "{id}");
    }

    private String validRequestId(String value) {
        if (value != null && value.matches("[A-Za-z0-9._-]{1,100}")) {
            return value;
        }
        return UUID.randomUUID().toString();
    }

    private boolean isHealthCheck(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }
}
