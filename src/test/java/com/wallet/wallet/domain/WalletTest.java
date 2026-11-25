package com.wallet.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletTest {

    private final Wallet wallet = Wallet.reconstitute(1L, 10L, 5_000, "EGP", Instant.now());

    @Test
    void creditAddsToBalance() {
        Wallet credited = wallet.credit(2_500);
        assertThat(credited.balanceMinor()).isEqualTo(7_500);
        assertThat(wallet.balanceMinor()).isEqualTo(5_000); // immutable
    }

    @Test
    void debitSubtractsFromBalance() {
        assertThat(wallet.debit(2_000).balanceMinor()).isEqualTo(3_000);
    }

    @Test
    void debitMoreThanBalanceIsRejected() {
        assertThatThrownBy(() -> wallet.debit(5_001))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void debitExactlyTheBalanceIsAllowed() {
        assertThat(wallet.debit(5_000).balanceMinor()).isZero();
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> wallet.credit(0)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> wallet.debit(-100)).isInstanceOf(InvalidAmountException.class);
    }
}
