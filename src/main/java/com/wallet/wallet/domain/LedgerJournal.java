package com.wallet.wallet.domain;

import java.util.List;
import java.util.UUID;

/**
 * A balanced set of ledger entries. The double-entry invariant
 * (entries of one journal sum to zero) is enforced here, in the domain.
 */
public record LedgerJournal(String journalId, List<LedgerEntry> entries) {

    public LedgerJournal {
        if (entries == null || entries.size() < 2) {
            throw new InvalidLedgerException("A journal needs at least two entries");
        }
        long sum = entries.stream().mapToLong(LedgerEntry::amountMinor).sum();
        if (sum != 0) {
            throw new InvalidLedgerException("Journal does not balance: entries sum to " + sum);
        }
    }

    public static LedgerJournal of(LedgerEntry... entries) {
        return new LedgerJournal(UUID.randomUUID().toString(), List.of(entries));
    }
}
