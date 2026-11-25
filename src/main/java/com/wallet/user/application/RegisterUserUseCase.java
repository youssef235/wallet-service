package com.wallet.user.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.user.domain.EmailAlreadyExistsException;
import com.wallet.user.domain.RefreshToken;
import com.wallet.user.domain.RefreshTokenRepository;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenHasher tokenHasher;
    private final TokenService tokenService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration refreshTokenLifetime;

    public RegisterUserUseCase(UserRepository userRepository,
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

    public AuthResult execute(RegisterUserCommand command) {
        User.validateRawPassword(command.rawPassword());
        return transactionRunner.inTransaction(() -> {
            if (userRepository.existsByEmail(command.email().trim().toLowerCase())) {
                throw new EmailAlreadyExistsException(command.email());
            }
            Instant now = clock.instant();
            User user = User.register(command.email(), command.fullName(),
                    passwordHasher.hash(command.rawPassword()), now);
            User saved = userRepository.save(user);
            return issueTokens(saved, now);
        });
    }

    private AuthResult issueTokens(User user, Instant now) {
        IssuedToken access = tokenService.generate(user.id(), user.email(), user.role());
        String refreshValue = RefreshToken.newTokenValue();
        Instant refreshExpiresAt = now.plus(refreshTokenLifetime);
        refreshTokenRepository.save(
                RefreshToken.issue(user.id(), tokenHasher.hash(refreshValue), refreshExpiresAt, now));
        return new AuthResult(access.value(), access.expiresAt(), refreshValue, refreshExpiresAt,
                UserResult.from(user));
    }
}
