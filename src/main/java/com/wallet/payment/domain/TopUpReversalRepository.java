package com.wallet.payment.domain;

public interface TopUpReversalRepository {

    TopUpReversal save(TopUpReversal reversal);

    /** Total reversed so far for a top-up, in minor units. */
    long sumAmountByTopupId(Long topupId);
}
