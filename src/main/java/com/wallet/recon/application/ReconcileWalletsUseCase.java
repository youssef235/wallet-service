package com.wallet.recon.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.WalletRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies the ledger against the cached wallet balances in one consistent
 * snapshot (one grouped query, read-only transaction), plus the two global
 * invariants of a double-entry ledger: every journal nets to zero, and the
 * whole ledger nets to zero. Any drift means a bug and is logged loudly.
 */
public class ReconcileWalletsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconcileWalletsUseCase.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRunner transactionRunner;

    public ReconcileWalletsUseCase(WalletRepository walletRepository,
                                   LedgerEntryRepository ledgerEntryRepository,
                                   TransactionRunner transactionRunner) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRunner = transactionRunner;
    }

    public record ReconciliationReport(List<String> mismatches) {

        public boolean clean() {
            return mismatches.isEmpty();
        }
    }

    public ReconciliationReport execute() {
        return transactionRunner.inReadOnlyTransaction(() -> {
            List<String> mismatches = new ArrayList<>();

            for (var drift : ledgerEntryRepository.findWalletBalanceDrift()) {
                mismatches.add("Wallet " + drift.walletId() + ": cached balance "
                        + drift.cachedBalanceMinor() + " != ledger balance " + drift.ledgerBalanceMinor());
            }

            long ledgerTotal = ledgerEntryRepository.sumAllEntries();
            if (ledgerTotal != 0) {
                mismatches.add("Ledger does not net to zero: total " + ledgerTotal);
            }

            for (String journalId : ledgerEntryRepository.findUnbalancedJournalIds()) {
                mismatches.add("Journal " + journalId + " does not net to zero");
            }

            mismatches.forEach(m -> log.error("RECONCILIATION MISMATCH - {}", m));
            return new ReconciliationReport(mismatches);
        });
    }
}
