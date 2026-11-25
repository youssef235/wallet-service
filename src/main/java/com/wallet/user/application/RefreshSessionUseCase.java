package com.wallet.user.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.shared.domain.NotFoundException;
import com.wallet.user.domain.InvalidCredentialsException;
import com.wallet.user.domain.RefreshToken;
import com.wallet.user.domain.RefreshTokenRepository;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Rotates a refresh token: the presented token is revoked and a new pair is issued.
 * Reusing an already-rotated token revokes every session of that user (theft detection).
 */
public class RefreshSessionUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final TokenService tokenService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration refreshTokenLifetime;

    public RefreshSessionUseCase(UserRepository userRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 TokenHasher tokenHasher,
                                 TokenService tokenService,
                                 TransactionRunner transactionRunner,
                                 Clock clock,
                                 Duration refreshTokenLifetime) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.tokenService = tokenService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    public AuthResult execute(String presentedToken) {
        return transactionRunner.inTransaction(() -> {
            Instant now = clock.instant();
            RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHasher.hash(presentedToken))
                    .orElseThrow(InvalidCredentialsException::new);

            if (stored.revoked()) {
                // A rotated token was presented again: treat as token theft and kill all sessions.
                refreshTokenRepository.revokeAllForUser(stored.userId());
                throw new InvalidCredentialsException();
            }
            if (!stored.isUsableAt(now)) {
                throw new InvalidCredentialsException();
            }

            User user = userRepository.findById(stored.userId())
                    .orElseThrow(() -> new NotFoundException("User not found"));

            String newRefreshValue = RefreshToken.newTokenValue();
            Instant newRefreshExpiresAt = now.plus(refreshTokenLifetime);
            RefreshToken rotated = stored.rotate(tokenHasher.hash(newRefreshValue));
            refreshTokenRepository.save(rotated);
            refreshTokenRepository.save(
                    RefreshToken.issue(user.id(), tokenHasher.hash(newRefreshValue), newRefreshExpiresAt, now));

            IssuedToken access = tokenService.generate(user.id(), user.email(), user.role());
            return new AuthResult(access.value(), access.expiresAt(), newRefreshValue, newRefreshExpiresAt,
                    UserResult.from(user));
        });
    }
}
