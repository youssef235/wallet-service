package com.wallet.payment.application;

import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpStatus;
import com.wallet.shared.application.TransactionRunner;
import com.wallet.wallet.domain.LedgerAccount;
import com.wallet.wallet.domain.LedgerEntry;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.LedgerJournal;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Applies an HMAC-verified refund/void callback from Paymob: the provider took
 * the money back, so the wallet is debited with a mirror journal exactly once
 * per reversal transaction id (unique column + row lock). The debit may take
 * the balance negative - the customer owes the money back; that is alerted on.
 */
public class HandlePaymobReversalUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymobReversalUseCase.class);

    private final TopUpRepository topUpRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public HandlePaymobReversalUseCase(TopUpRepository topUpRepository,
                                       WalletRepository walletRepository,
                                       LedgerEntryRepository ledgerEntryRepository,
                                       TransactionRunner transactionRunner,
                                       Clock clock) {
        this.topUpRepository = topUpRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public ReversalProcessingResult execute(Long reversalTransactionId,
                                            Long originalTransactionId,
                                            long amountCents) {
        try {
            return transactionRunner.inTransaction(() ->
                    process(reversalTransactionId, originalTransactionId, amountCents));
        } catch (DataIntegrityViolationException duplicateReversal) {
            // Two reversal callbacks raced; the unique constraint on
            // reversal_transaction_id won.
            return ReversalProcessingResult.ALREADY_PROCESSED;
        }
    }

    private ReversalProcessingResult process(Long reversalTransactionId,
                                             Long originalTransactionId,
                                             long amountCents) {
        var locked = topUpRepository.findByPaymobTransactionIdForUpdate(originalTransactionId);
        if (locked.isEmpty()) {
            log.error("Reversal {} references unknown Paymob transaction {}",
                    reversalTransactionId, originalTransactionId);
            return ReversalProcessingResult.UNKNOWN_TRANSACTION;
        }
        TopUp topUp = locked.get();

        if (topUp.reversalTransactionId() != null) {
            return ReversalProcessingResult.ALREADY_PROCESSED;
        }
        if (topUp.status() != TopUpStatus.SUCCESS) {
            log.warn("Reversal {} for top-up {} in status {} - nothing to reverse",
                    reversalTransactionId, topUp.merchantOrderId(), topUp.status());
            return ReversalProcessingResult.IGNORED;
        }
        if (amountCents != topUp.amountMinor()) {
            // Partial refunds exist; reverse exactly what the provider says it took back.
            log.error("Reversal {} amount {} differs from top-up {} amount {}",
                    reversalTransactionId, amountCents, topUp.merchantOrderId(), topUp.amountMinor());
        }

        Instant now = clock.instant();
        Wallet wallet = walletRepository.findByUserIdForUpdate(topUp.userId())
                .orElseThrow(() -> new IllegalStateException("Wallet missing for user " + topUp.userId()));
        Wallet reversed = wallet.applyReversal(amountCents);
        if (reversed.balanceMinor() < 0) {
            log.error("ALERT: wallet {} went negative ({}) after reversal {} of top-up {}",
                    wallet.id(), reversed.balanceMinor(), reversalTransactionId, topUp.merchantOrderId());
        }
        walletRepository.save(reversed);

        String journalId = UUID.randomUUID().toString();
        String description = "Paymob reversal, transaction " + reversalTransactionId;
        LedgerJournal journal = new LedgerJournal(journalId, List.of(
                LedgerEntry.debit(journalId, LedgerAccount.ofWallet(wallet.id()), amountCents, description, now),
                LedgerEntry.credit(journalId, LedgerAccount.PAYMOB_CLEARING, amountCents, description, now)));
        ledgerEntryRepository.saveAll(journal);

        topUpRepository.save(topUp.markRefunded(reversalTransactionId));
        return ReversalProcessingResult.REVERSED;
    }
}
