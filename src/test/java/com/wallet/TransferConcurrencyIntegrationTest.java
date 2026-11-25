package com.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.support.NoOpNotificationService;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import com.wallet.wallet.application.TransferCommand;
import com.wallet.wallet.application.TransferUseCase;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hammers the transfer use case with concurrent opposite-direction transfers
 * (A->B and B->A at the same time). With pessimistic locking in a fixed order,
 * no deadlock may occur and money must be conserved exactly.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyIntegrationTest {

    @TestConfiguration
    static class MuteNotifications {
        @Bean
        @Primary
        NoOpNotificationService noOpNotificationService() {
            return new NoOpNotificationService();
        }
    }

    @Autowired
    private TransferUseCase transferUseCase;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentOppositeTransfersConserveMoneyWithoutDeadlock() throws Exception {
        Instant now = Instant.now();
        Long userA = transactionTemplate.execute(tx -> userRepository.save(
                User.register("usera@example.com", "User A", "hash", now)).id());
        Long userB = transactionTemplate.execute(tx -> userRepository.save(
                User.register("userb@example.com", "User B", "hash", now)).id());
        transactionTemplate.executeWithoutResult(tx -> {
            walletRepository.save(Wallet.openFor(userA, "EGP", now).credit(10_000));
            walletRepository.save(Wallet.openFor(userB, "EGP", now).credit(10_000));
        });

        int transfersPerDirection = 10;
        long amount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(2 * transfersPerDirection);
        CountDownLatch ready = new CountDownLatch(2 * transfersPerDirection);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < transfersPerDirection; i++) {
            String keyAB = "AB-" + UUID.randomUUID();
            String keyBA = "BA-" + UUID.randomUUID();
            pool.submit(() -> run(ready, go, failures,
                    () -> transferUseCase.execute(new TransferCommand(userA, "userb@example.com", amount, keyAB))));
            pool.submit(() -> run(ready, go, failures,
                    () -> transferUseCase.execute(new TransferCommand(userB, "usera@example.com", amount, keyBA))));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures.get()).isZero();

        var state = transactionTemplate.execute(tx -> {
            Wallet a = walletRepository.findByUserId(userA).orElseThrow();
            Wallet b = walletRepository.findByUserId(userB).orElseThrow();
            long ledgerA = ledgerEntryRepository.sumForWallet(a.id());
            long ledgerB = ledgerEntryRepository.sumForWallet(b.id());
            return List.of(a.balanceMinor(), b.balanceMinor(), ledgerA, ledgerB);
        });

        // Equal amounts went both ways: balances are back to the starting point.
        assertThat(state.get(0)).isEqualTo(10_000);
        assertThat(state.get(1)).isEqualTo(10_000);
        // The seed funding bypassed the ledger (test setup only), so each wallet's
        // ledger net is 0: the 10 in and 10 out cancel exactly. Zero drift.
        assertThat(state.get(2)).isEqualTo(0);
        assertThat(state.get(3)).isEqualTo(0);
    }

    private void run(CountDownLatch ready, CountDownLatch go, AtomicInteger failures, Runnable work) {
        try {
            ready.countDown();
            go.await();
            work.run();
        } catch (Exception e) {
            failures.incrementAndGet();
        }
    }
}
