package com.wallet.shared.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple fixed-window rate limiting. Per-IP on the auth endpoints (brute-force
 * and registration spam) and per-user on transfers. In-memory: correct for a
 * single instance; for multiple instances use a shared store or the proxy.
 *
 * Hardening notes:
 * - X-Forwarded-For is trusted only when the direct peer is a private/loopback
 *   address (i.e. our reverse proxy); a client reaching us directly cannot
 *   spoof its IP to dodge the limit.
 * - The keyspace is bounded: expired windows are swept under pressure, and a
 *   full table fails closed (429) instead of wiping everyone's counters.
 * - /refresh has its own looser bucket: browsers refresh on a schedule, and
 *   users behind CGNAT share one IP.
 */
@Component
class RateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int AUTH_LIMIT_PER_IP = 10;
    private static final int REFRESH_LIMIT_PER_IP = 30;
    private static final int TRANSFER_LIMIT_PER_USER = 30;
    private static final int MAX_KEYS = 100_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String key = null;
        int limit = 0;
        if (path.equals("/api/v1/auth/refresh")) {
            key = "refresh:" + clientIp(request);
            limit = REFRESH_LIMIT_PER_IP;
        } else if (path.startsWith("/api/v1/auth/")) {
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
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_KEYS) {
            sweepExpired(now);
            if (windows.size() > MAX_KEYS && !windows.containsKey(key)) {
                // Under flood: fail closed rather than reset everyone's counters.
                return false;
            }
        }
        Window window = windows.compute(key, (k, existing) ->
                existing == null || now - existing.startMs > WINDOW_MS ? new Window(now) : existing);
        return window.count.incrementAndGet() <= limit;
    }

    private void sweepExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().startMs > WINDOW_MS);
    }

    private static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (isPrivatePeer(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remote;
    }

    /** Only a proxy on the machine or the private network may speak for other IPs. */
    private static boolean isPrivatePeer(String remote) {
        try {
            InetAddress address = InetAddress.getByName(remote);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception unknown) {
            return false;
        }
    }

    private static final class Window {
        private final long startMs;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
