package com.wallet.payment.application;

import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpReversal;
import com.wallet.payment.domain.TopUpReversalRepository;
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
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Applies an HMAC-verified refund/void callback from Paymob: the provider took
 * money back, so the wallet is debited with a mirror journal. Partial refunds
 * are supported: every reversal is recorded exactly once in topup_reversals
 * (unique reversal transaction id + row lock on the top-up), and the top-up is
 * marked REFUNDED only once the reversed total reaches the original amount.
 * The debit may take the balance negative - the customer owes the money back;
 * that is alerted on.
 *
 * The top-up is located by its Paymob order id, which is part of the HMAC
 * signature; the unsigned parent_transaction id is only a fallback.
 */
public class HandlePaymobReversalUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymobReversalUseCase.class);

    private final TopUpRepository topUpRepository;
    private final TopUpReversalRepository topUpReversalRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public HandlePaymobReversalUseCase(TopUpRepository topUpRepository,
                                       TopUpReversalRepository topUpReversalRepository,
                                       WalletRepository walletRepository,
                                       LedgerEntryRepository ledgerEntryRepository,
                                       TransactionRunner transactionRunner,
                                       Clock clock) {
        this.topUpRepository = topUpRepository;
        this.topUpReversalRepository = topUpReversalRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public ReversalProcessingResult execute(Long reversalTransactionId, Long paymobOrderId,
                                            Long originalTransactionId, long amountCents) {
        try {
            return transactionRunner.inTransaction(() ->
                    process(reversalTransactionId, paymobOrderId, originalTransactionId, amountCents));
        } catch (DataIntegrityViolationException duplicateReversal) {
            // Two callbacks for the same reversal raced; the unique constraint on
            // topup_reversals.reversal_transaction_id won.
            return ReversalProcessingResult.ALREADY_PROCESSED;
        }
    }

    private ReversalProcessingResult process(Long reversalTransactionId, Long paymobOrderId,
                                             Long originalTransactionId, long amountCents) {
        Optional<TopUp> locked = paymobOrderId != null
                ? topUpRepository.findByPaymobOrderIdForUpdate(paymobOrderId)
                : topUpRepository.findByPaymobTransactionIdForUpdate(originalTransactionId);
        if (locked.isEmpty()) {
            log.error("Reversal {} references unknown Paymob order {} / transaction {}",
                    reversalTransactionId, paymobOrderId, originalTransactionId);
            return ReversalProcessingResult.UNKNOWN_TRANSACTION;
        }
        TopUp topUp = locked.get();

        if (topUp.status() == TopUpStatus.REFUNDED) {
            log.error("Reversal {} for already fully refunded top-up {} - ignored",
                    reversalTransactionId, topUp.merchantOrderId());
            return ReversalProcessingResult.IGNORED;
        }
        if (topUp.status() != TopUpStatus.SUCCESS) {
            log.warn("Reversal {} for top-up {} in status {} - nothing to reverse",
                    reversalTransactionId, topUp.merchantOrderId(), topUp.status());
            return ReversalProcessingResult.IGNORED;
        }

        Instant now = clock.instant();
        topUpReversalRepository.save(
                TopUpReversal.record(topUp.id(), reversalTransactionId, amountCents, now));

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

        long reversedTotal = topUpReversalRepository.sumAmountByTopupId(topUp.id());
        if (reversedTotal >= topUp.amountMinor()) {
            topUpRepository.save(topUp.markRefunded());
        } else {
            log.info("Partial reversal {} of top-up {}: {} of {} reversed so far",
                    reversalTransactionId, topUp.merchantOrderId(), reversedTotal, topUp.amountMinor());
        }
        return ReversalProcessingResult.REVERSED;
    }
}
