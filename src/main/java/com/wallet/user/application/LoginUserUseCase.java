package com.wallet.user.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.user.domain.InvalidCredentialsException;
import com.wallet.user.domain.RefreshToken;
import com.wallet.user.domain.RefreshTokenRepository;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenHasher tokenHasher;
    private final TokenService tokenService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration refreshTokenLifetime;

    public LoginUserUseCase(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            PasswordHasher passwordHasher,
                            TokenHasher tokenHasher,
                            TokenService tokenService,
                            TransactionRunner transactionRunner,
                            Clock clock,
                            Duration refreshTokenLifetime) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.tokenHasher = tokenHasher;
        this.tokenService = tokenService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    public AuthResult execute(LoginUserCommand command) {
        return transactionRunner.inTransaction(() -> {
            User user = userRepository.findByEmail(command.email().trim().toLowerCase())
                    .orElseThrow(InvalidCredentialsException::new);
            if (!passwordHasher.matches(command.rawPassword(), user.passwordHash())) {
                throw new InvalidCredentialsException();
            }
            Instant now = clock.instant();
            IssuedToken access = tokenService.generate(user.id(), user.email(), user.role());
            String refreshValue = RefreshToken.newTokenValue();
            Instant refreshExpiresAt = now.plus(refreshTokenLifetime);
            refreshTokenRepository.save(
                    RefreshToken.issue(user.id(), tokenHasher.hash(refreshValue), refreshExpiresAt, now));
            return new AuthResult(access.value(), access.expiresAt(), refreshValue, refreshExpiresAt,
                    UserResult.from(user));
        });
    }
}
