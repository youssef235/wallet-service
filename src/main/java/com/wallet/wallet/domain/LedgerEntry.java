package com.wallet.wallet.domain;

import java.time.Instant;

/**
 * One line of a double-entry journal. Positive amounts credit the account,
 * negative amounts debit it. Entries of one journal always sum to zero.
 */
public record LedgerEntry(Long id, String journalId, LedgerAccount account, long amountMinor,
                          String description, Instant createdAt) {

    public static LedgerEntry credit(String journalId, LedgerAccount account, long amountMinor,
                                     String description, Instant now) {
        return new LedgerEntry(null, journalId, account, amountMinor, description, now);
    }

    public static LedgerEntry debit(String journalId, LedgerAccount account, long amountMinor,
                                    String description, Instant now) {
        return new LedgerEntry(null, journalId, account, -amountMinor, description, now);
    }
}
