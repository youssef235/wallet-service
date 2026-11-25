package com.wallet.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple fixed-window rate limiting. Per-IP on the auth endpoints (brute-force
 * and registration spam) and per-user on transfers. In-memory: correct for a
 * single instance; for multiple instances use a shared store or the proxy.
 */
@Component
class RateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int AUTH_LIMIT_PER_IP = 10;
    private static final int TRANSFER_LIMIT_PER_USER = 30;
    private static final int MAX_KEYS = 100_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String key = null;
        int limit = 0;
        if (path.startsWith("/api/v1/auth/")) {
            key = "auth:" + clientIp(request);
            limit = AUTH_LIMIT_PER_IP;
        } else if (path.equals("/api/v1/wallet/transfers") && "POST".equalsIgnoreCase(request.getMethod())) {
            String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : clientIp(request);
            key = "transfer:" + user;
            limit = TRANSFER_LIMIT_PER_USER;
        }

        if (key != null && !allow(key, limit)) {
            response.setStatus(429);
            response.setContentType("application/json");
            // Same shape as ErrorResponse.
            response.getWriter().write("{\"code\":\"RATE_LIMITED\","
                    + "\"message\":\"Too many requests, try again in a minute\","
                    + "\"violations\":[],\"timestamp\":\"" + java.time.Instant.now() + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        if (windows.size() > MAX_KEYS) {
            windows.clear(); // fail open on memory pressure rather than leak
        }
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) ->
                existing == null || now - existing.startMs > WINDOW_MS ? new Window(now) : existing);
        return window.count.incrementAndGet() <= limit;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final long startMs;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
