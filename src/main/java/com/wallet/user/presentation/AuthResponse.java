package com.wallet.user.presentation;

import com.wallet.user.application.AuthResult;
import java.time.Instant;

record AuthResponse(String accessToken,
                    Instant accessTokenExpiresAt,
                    String refreshToken,
                    Instant refreshTokenExpiresAt,
                    UserBody user) {

    record UserBody(Long id, String email, String fullName, String role) {
    }

    static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.accessToken(), result.accessTokenExpiresAt(),
                result.refreshToken(), result.refreshTokenExpiresAt(),
                new UserBody(result.user().id(), result.user().email(), result.user().fullName(),
                        result.user().role()));
    }
}
