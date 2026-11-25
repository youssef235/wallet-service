package com.wallet.shared.presentation;

import com.wallet.shared.domain.ForbiddenOperationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Extracts the authenticated user id from the JWT principal.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return Long.valueOf(jwt.getSubject());
        }
        throw new ForbiddenOperationException("Authenticated principal is required");
    }
}
