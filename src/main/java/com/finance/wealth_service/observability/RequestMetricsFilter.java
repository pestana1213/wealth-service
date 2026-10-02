package com.finance.wealth_service.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Records one bounded-cardinality metric and one structured log per HTTP request. */
@Component
public class RequestMetricsFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestMetricsFilter.class);
    private final MeterRegistry registry;

    public RequestMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Timer.Sample sample = Timer.start(registry);
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            String method = request.getMethod();
            String route = route(request);
            String status = Integer.toString(response.getStatus());
            registry.counter("wealth.http.requests", "method", method, "route", route, "status", status).increment();
            sample.stop(Timer.builder("wealth.http.request.duration")
                    .tag("method", method).tag("route", route).tag("status", status).register(registry));
            log.debug("http_request method={} route={} status={} duration_ms={}", method, route, status,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }

    private String route(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator")) return "/actuator";
        if (uri.matches("/users/[0-9a-fA-F-]+/dashboard")) return "/users/{userId}/dashboard";
        if (uri.matches("/users/[0-9a-fA-F-]+/(accounts|buckets|goals)")) return "/users/{userId}/" + uri.substring(uri.lastIndexOf('/') + 1);
        if (uri.matches("/users/[0-9a-fA-F-]+")) return "/users/{userId}";
        return uri;
    }
}
