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

    /** Pre-computed BCrypt hash of a random password, used only to burn equal time. */
    private static final String DUMMY_HASH = "$2a$10$lOMivm.j9amtjXzt3fO9VeKXHrJesDxS.FopOPRiI8aGvyP.wK4.u";

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
            var found = userRepository.findByEmail(command.email().trim().toLowerCase());
            if (found.isEmpty()) {
                // Compare against a dummy hash so unknown-email logins take the same
                // time as wrong-password logins and do not reveal registered emails.
                passwordHasher.matches(command.rawPassword(), DUMMY_HASH);
                throw new InvalidCredentialsException();
            }
            User user = found.get();
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
