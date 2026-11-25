package com.wallet.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LedgerJournalTest {

    private static final Instant NOW = Instant.now();

    @Test
    void balancedJournalIsAccepted() {
        LedgerJournal journal = LedgerJournal.of(
                LedgerEntry.debit(null, LedgerAccount.PAYMOB_CLEARING, 1_000, "top-up", NOW),
                LedgerEntry.credit(null, LedgerAccount.ofWallet(7L), 1_000, "top-up", NOW));
        assertThat(journal.entries()).hasSize(2);
        assertThat(journal.journalId()).isNotBlank();
    }

    @Test
    void unbalancedJournalIsRejected() {
        assertThatThrownBy(() -> LedgerJournal.of(
                LedgerEntry.debit(null, LedgerAccount.PAYMOB_CLEARING, 1_000, "broken", NOW),
                LedgerEntry.credit(null, LedgerAccount.ofWallet(7L), 999, "broken", NOW)))
                .isInstanceOf(InvalidLedgerException.class)
                .hasMessageContaining("does not balance");
    }

    @Test
    void singleEntryJournalIsRejected() {
        assertThatThrownBy(() -> LedgerJournal.of(
                LedgerEntry.credit(null, LedgerAccount.ofWallet(7L), 1_000, "broken", NOW)))
                .isInstanceOf(InvalidLedgerException.class);
    }
}
