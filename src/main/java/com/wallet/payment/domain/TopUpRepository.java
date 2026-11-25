package com.wallet.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TopUpRepository {

    TopUp save(TopUp topUp);

    Optional<TopUp> findByMerchantOrderId(String merchantOrderId);

    /** SELECT ... FOR UPDATE: serializes concurrent webhooks for the same top-up. */
    Optional<TopUp> findByMerchantOrderIdForUpdate(String merchantOrderId);

    boolean existsByPaymobTransactionId(Long paymobTransactionId);

    /** SELECT ... FOR UPDATE: serializes a reversal with anything else touching this top-up. */
    Optional<TopUp> findByPaymobTransactionIdForUpdate(Long paymobTransactionId);

    List<TopUp> findByUserId(Long userId);

    List<TopUp> findPendingOlderThan(Instant cutoff);
}
