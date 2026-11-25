package com.wallet.wallet.domain;

import java.util.List;

public interface LedgerEntryRepository {

    void saveAll(LedgerJournal journal);

    /** Keyset pagination: newest first, entries with id < beforeId (null = first page). */
    List<LedgerEntry> findByWalletId(Long walletId, Long beforeId, int limit);

    /** Sum of all ledger amounts for one wallet account; the wallet's "real" balance. */
    long sumForWallet(Long walletId);

    /** Single-snapshot check: wallets whose cached balance disagrees with the ledger. */
    List<WalletBalanceDrift> findWalletBalanceDrift();

    /** Sum over every ledger entry; the double-entry ledger must always net to zero. */
    long sumAllEntries();

    /** Journals whose entries do not net to zero (must never exist). */
    List<String> findUnbalancedJournalIds();

    record WalletBalanceDrift(Long walletId, long cachedBalanceMinor, long ledgerBalanceMinor) {
    }
}
