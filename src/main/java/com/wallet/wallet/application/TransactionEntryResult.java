package com.wallet.wallet.application;

import com.wallet.wallet.domain.LedgerEntry;

import java.time.Instant;

public record TransactionEntryResult(String journalId, String direction, long amountMinor,
                                     String description, Instant createdAt) {

    public static TransactionEntryResult from(LedgerEntry entry) {
        return new TransactionEntryResult(entry.journalId(),
                entry.amountMinor() >= 0 ? "CREDIT" : "DEBIT",
                Math.abs(entry.amountMinor()), entry.description(), entry.createdAt());
    }
}
