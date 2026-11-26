package com.wallet.user.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallet.shared.domain.TooManyRequestsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoginAttemptGuardTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-26T00:00:00Z"));
    private final Clock clock = Clock.fixed(now.get(), ZoneOffset.UTC);
    private final LoginAttemptGuard guard = new LoginAttemptGuard(new MutableClock());

    private final class MutableClock extends Clock {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @Test
    void theSixthFailureIsRefused() {
        String email = "user@example.com";
        for (int i = 0; i < 5; i++) {
            guard.onFailure(email);
        }
        assertThatThrownBy(() -> guard.checkAllowed(email))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void successClearsTheFailures() {
        String email = "user@example.com";
        for (int i = 0; i < 5; i++) {
            guard.onFailure(email);
        }
        guard.onSuccess(email);
        assertThatCode(() -> guard.checkAllowed(email)).doesNotThrowAnyException();
    }

    @Test
    void theBlockExpiresWithTheWindow() {
        String email = "user@example.com";
        for (int i = 0; i < 5; i++) {
            guard.onFailure(email);
        }
        now.set(now.get().plusSeconds(11 * 60));
        assertThatCode(() -> guard.checkAllowed(email)).doesNotThrowAnyException();
    }

    @Test
    void failuresAreCountedPerAccount() {
        for (int i = 0; i < 5; i++) {
            guard.onFailure("a@example.com");
        }
        assertThatCode(() -> guard.checkAllowed("b@example.com")).doesNotThrowAnyException();
    }
}
