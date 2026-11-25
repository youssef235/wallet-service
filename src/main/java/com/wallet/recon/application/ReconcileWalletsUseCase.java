package com.wallet.recon.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-derives every wallet balance from the ledger and compares it to the cached
 * balance on the wallet row. Because the balance is only a projection of the
 * double-entry ledger, any drift means a bug: it is logged loudly and reported.
 */
public class ReconcileWalletsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReconcileWalletsUseCase.class);

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRunner transactionRunner;

    public ReconcileWalletsUseCase(WalletRepository walletRepository,
                                   LedgerEntryRepository ledgerEntryRepository,
                                   TransactionRunner transactionRunner) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRunner = transactionRunner;
    }

    public record ReconciliationReport(int walletsChecked, List<String> mismatches) {
    }

    public ReconciliationReport execute() {
        return transactionRunner.inTransaction(() -> {
            List<String> mismatches = new ArrayList<>();
            List<Wallet> wallets = walletRepository.findAll();
            for (Wallet wallet : wallets) {
                long ledgerBalance = ledgerEntryRepository.sumForWallet(wallet.id());
                if (ledgerBalance != wallet.balanceMinor()) {
                    String message = "Wallet " + wallet.id() + " (user " + wallet.userId()
                            + "): cached balance " + wallet.balanceMinor()
                            + " != ledger balance " + ledgerBalance;
                    log.error("RECONCILIATION MISMATCH - {}", message);
                    mismatches.add(message);
                }
            }
            return new ReconciliationReport(wallets.size(), mismatches);
        });
    }
}
