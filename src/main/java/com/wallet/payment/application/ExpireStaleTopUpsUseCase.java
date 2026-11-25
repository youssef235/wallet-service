package com.wallet.payment.application;

import com.wallet.payment.domain.TopUpRepository;
import com.wallet.shared.application.TransactionRunner;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Marks PENDING top-ups older than the configured age as EXPIRED. */
public class ExpireStaleTopUpsUseCase {

    private final TopUpRepository topUpRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final Duration maxAge;

    public ExpireStaleTopUpsUseCase(TopUpRepository topUpRepository,
                                    TransactionRunner transactionRunner,
                                    Clock clock,
                                    Duration maxAge) {
        this.topUpRepository = topUpRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.maxAge = maxAge;
    }

    public int execute() {
        return transactionRunner.inTransaction(() -> {
            Instant cutoff = clock.instant().minus(maxAge);
            var stale = topUpRepository.findPendingOlderThan(cutoff);
            stale.forEach(topUp -> topUpRepository.save(topUp.markExpired()));
            return stale.size();
        });
    }
}
