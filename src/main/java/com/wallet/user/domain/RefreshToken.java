package com.wallet.user.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A long-lived refresh token. Only the SHA-256 hash of the token value is stored,
 * so a database leak does not expose usable tokens.
 */
public class RefreshToken {

    private final Long id;
    private final Long userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final boolean revoked;
    private final String replacedByHash;
    private final Instant createdAt;

    private RefreshToken(Long id, Long userId, String tokenHash, Instant expiresAt,
                         boolean revoked, String replacedByHash, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.replacedByHash = replacedByHash;
        this.createdAt = createdAt;
    }

    public static String newTokenValue() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    public static RefreshToken issue(Long userId, String tokenHash, Instant expiresAt, Instant now) {
        return new RefreshToken(null, userId, tokenHash, expiresAt, false, null, now);
    }

    public static RefreshToken reconstitute(Long id, Long userId, String tokenHash, Instant expiresAt,
                                            boolean revoked, String replacedByHash, Instant createdAt) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revoked, replacedByHash, createdAt);
    }

    public boolean isUsableAt(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public RefreshToken rotate(String newTokenHash) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, true, newTokenHash, createdAt);
    }

    public RefreshToken revoke() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, true, replacedByHash, createdAt);
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
    public boolean revoked() { return revoked; }
    public String replacedByHash() { return replacedByHash; }
    public Instant createdAt() { return createdAt; }
}
