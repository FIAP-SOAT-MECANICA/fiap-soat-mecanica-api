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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = validRequestId(request.getHeader(REQUEST_ID_HEADER));
        long start = System.nanoTime();

        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            if (!isHealthCheck(request)) {
                Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String route = routePattern == null ? request.getRequestURI() : routePattern.toString();
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                log.info("http_request_completed method={} route={} status={} durationMs={} requestId={}",
                        request.getMethod(), route, response.getStatus(), durationMs, requestId);
            }
        }
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
