package com.wallet.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallet.support.FakeTransactionRunner;
import com.wallet.user.domain.InvalidCredentialsException;
import com.wallet.user.domain.RefreshToken;
import com.wallet.user.domain.RefreshTokenRepository;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import com.wallet.user.domain.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshSessionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-25T20:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final TokenHasher tokenHasher = mock(TokenHasher.class);
    private final TokenService tokenService = mock(TokenService.class);

    private RefreshSessionUseCase useCase;

    private final User user = User.reconstitute(1L, "ahmed@example.com", "Ahmed Ali", "hash",
            UserRole.USER, NOW);

    @BeforeEach
    void setUp() {
        useCase = new RefreshSessionUseCase(userRepository, refreshTokenRepository, tokenHasher,
                tokenService, new FakeTransactionRunner(), Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(30));
        when(tokenHasher.hash("old-token")).thenReturn("old-hash");
        when(tokenHasher.hash(anyString())).thenAnswer(inv -> "hash-of-" + inv.getArgument(0));
        when(tokenService.generate(anyLong(), anyString(), any()))
                .thenReturn(new IssuedToken("new-access", NOW.plus(Duration.ofHours(1))));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void validTokenRotatesAndIssuesNewPair() {
        RefreshToken stored = RefreshToken.issue(1L, "hash-of-old-token", NOW.plus(Duration.ofDays(10)), NOW);
        when(refreshTokenRepository.findByTokenHash("hash-of-old-token"))
                .thenReturn(Optional.of(stored));

        AuthResult result = useCase.execute("old-token");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isNotBlank();
        // Two saves: the rotated (revoked) old token and the newly issued one.
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
    }

    @Test
    void reusingARotatedTokenRevokesAllSessions() {
        RefreshToken rotated = RefreshToken.issue(1L, "hash-of-old-token", NOW.plus(Duration.ofDays(10)), NOW)
                .rotate("hash-of-new-token");
        when(refreshTokenRepository.findByTokenHash("hash-of-old-token"))
                .thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> useCase.execute("old-token"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository).revokeAllForUser(1L);
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken expired = RefreshToken.issue(1L, "hash-of-old-token", NOW.minus(Duration.ofDays(1)), NOW);
        when(refreshTokenRepository.findByTokenHash("hash-of-old-token"))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase.execute("old-token"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).revokeAllForUser(anyLong());
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("nonsense"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
