package com.wallet.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Guards the login timing equalization: the dummy hash must be a syntactically
 * valid BCrypt hash so matches() runs the full key schedule (an invalid hash
 * fast-fails in Spring and would re-expose the unknown-email timing difference).
 */
class LoginDummyHashTest {

    @Test
    void theDummyHashIsValidBCryptAndRunsTheFullComparison() {
        assertThat(LoginUserUseCase.DUMMY_HASH).hasSize(60);

        var encoder = new BCryptPasswordEncoder();
        long start = System.nanoTime();
        boolean matches = encoder.matches("any-password", LoginUserUseCase.DUMMY_HASH);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(matches).isFalse();
        // A malformed hash returns in microseconds; the real comparison takes tens of ms.
        assertThat(elapsedMs).isGreaterThan(10);
    }
}
