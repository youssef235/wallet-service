package com.wallet.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class RateLimitingFilterTest {

    private final RateLimitingFilter filter = new RateLimitingFilter();

    private HttpServletRequest request(String path, String remoteAddr, String xff) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
        return request;
    }

    private int statusOf(HttpServletRequest request, int times) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        int status = 200;
        for (int i = 0; i < times; i++) {
            final int[] last = {200};
            filter.doFilterInternal(request, response, (FilterChain) (req, res) -> {});
            org.mockito.Mockito.verify(response, org.mockito.Mockito.atMost(1)).setStatus(429);
        }
        var statuses = org.mockito.Mockito.mockingDetails(response).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("setStatus"))
                .map(inv -> (int) inv.getArgument(0)).toList();
        return statuses.isEmpty() ? status : statuses.get(statuses.size() - 1);
    }

    @Test
    void theEleventhAuthCallInAMinuteIsRejected() throws Exception {
        var request = request("/api/v1/auth/login", "203.0.113.9", null);
        assertThat(statusOf(request, 10)).isEqualTo(200);
        assertThat(statusOf(request, 1)).isEqualTo(429);
    }

    @Test
    void forwardedForIsIgnoredWhenThePeerIsPublic() throws Exception {
        // Spoofer on a public address varies XFF; without trust it still hits one bucket.
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request("/api/v1/auth/login", "203.0.113.9", "198.51.100." + i),
                    mock(HttpServletResponse.class), (FilterChain) (req, res) -> {});
        }
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilterInternal(request("/api/v1/auth/login", "203.0.113.9", "198.51.100.99"),
                response, (FilterChain) (req, res) -> {});
        org.mockito.Mockito.verify(response).setStatus(429);
    }

    @Test
    void forwardedForIsHonoredWhenThePeerIsPrivate() throws Exception {
        // Behind our proxy (private address), distinct client IPs get distinct buckets.
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(request("/api/v1/auth/login", "10.0.0.1", "198.51.100.1"),
                    mock(HttpServletResponse.class), (FilterChain) (req, res) -> {});
        }
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        // A different forwarded IP is a different bucket and still allowed.
        filter.doFilterInternal(request("/api/v1/auth/login", "10.0.0.1", "198.51.100.2"),
                response, (FilterChain) (req, res) -> {});
        org.mockito.Mockito.verify(response, org.mockito.Mockito.never()).setStatus(429);
    }

    @Test
    void refreshHasItsOwnLooserBucket() throws Exception {
        var request = request("/api/v1/auth/refresh", "203.0.113.9", null);
        for (int i = 0; i < 30; i++) {
            filter.doFilterInternal(request, mock(HttpServletResponse.class), (FilterChain) (req, res) -> {});
        }
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        filter.doFilterInternal(request, response, (FilterChain) (req, res) -> {});
        org.mockito.Mockito.verify(response).setStatus(429);
    }
}
