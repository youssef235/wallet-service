package com.wallet.wallet.application;

import java.util.List;

public record TransactionHistoryResult(List<TransactionEntryResult> entries, Long nextBeforeId, int limit) {
}
