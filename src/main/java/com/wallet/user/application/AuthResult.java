package com.wallet.user.application;

import java.time.Instant;

public record AuthResult(String accessToken,
                         Instant accessTokenExpiresAt,
                         String refreshToken,
                         Instant refreshTokenExpiresAt,
                         UserResult user) {
}
