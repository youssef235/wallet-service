package com.wallet.user.infrastructure.security;

import com.wallet.user.application.IssuedToken;
import com.wallet.user.application.TokenService;
import com.wallet.user.domain.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtTokenService implements TokenService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration lifetime;

    JwtTokenService(JwtEncoder jwtEncoder, Clock clock,
                    @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.lifetime = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public IssuedToken generate(Long userId, String email, UserRole role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(lifetime);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("wallet-service")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", email)
                .claim("role", role.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
