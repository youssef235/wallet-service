package com.wallet.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wallet.support.TestcontainersConfiguration;
import com.wallet.user.domain.InvalidCredentialsException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RefreshTokenIntegrationTest {

    @Autowired
    private RegisterUserUseCase registerUser;
    @Autowired
    private RefreshSessionUseCase refreshSession;

    private AuthResult newUser() {
        return registerUser.execute(new RegisterUserCommand(
                "refresh-" + UUID.randomUUID() + "@example.com", "Refresh Test", "password-123"));
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeChainThroughARealTransaction() {
        String first = newUser().refreshToken();
        String second = refreshSession.execute(first).refreshToken();

        assertThatThrownBy(() -> refreshSession.execute(first))
                .isInstanceOf(InvalidCredentialsException.class);
        // If the revoke had been rolled back together with the exception, 'second' would still work.
        assertThatThrownBy(() -> refreshSession.execute(second))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void twoParallelRefreshesOfTheSameTokenCannotBothSucceed() throws Exception {
        String token = newUser().refreshToken();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    refreshSession.execute(token);
                    return true;
                } catch (InvalidCredentialsException rejected) {
                    return false;
                }
            }));
        }
        go.countDown();
        long successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
    }
}
