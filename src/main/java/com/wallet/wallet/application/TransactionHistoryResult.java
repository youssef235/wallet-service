package com.wallet.wallet.application;

import java.util.List;

public record TransactionHistoryResult(List<TransactionEntryResult> entries, long total, int offset, int limit) {
}
