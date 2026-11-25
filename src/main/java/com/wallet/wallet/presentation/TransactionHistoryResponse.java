package com.wallet.wallet.presentation;

import com.wallet.wallet.application.TransactionHistoryResult;
import java.time.Instant;
import java.util.List;

record TransactionHistoryResponse(List<Entry> entries, Long nextBeforeId, int limit) {

    record Entry(String journalId, String direction, long amountMinor, String description, Instant createdAt) {
    }

    static TransactionHistoryResponse from(TransactionHistoryResult result) {
        var entries = result.entries().stream()
                .map(e -> new Entry(e.journalId(), e.direction(), e.amountMinor(), e.description(), e.createdAt()))
                .toList();
        return new TransactionHistoryResponse(entries, result.nextBeforeId(), result.limit());
    }
}
