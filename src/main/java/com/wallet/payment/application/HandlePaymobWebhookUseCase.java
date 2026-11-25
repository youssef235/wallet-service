package com.wallet.payment.application;

import com.wallet.notification.application.NotificationService;
import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.shared.application.TransactionRunner;
import com.wallet.user.domain.UserRepository;
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
 * Applies an HMAC-verified Paymob webhook exactly once:
 *
 * - Fast path: a top-up already carrying this Paymob transaction id means we
 *   processed this webhook before (Paymob retries callbacks) - done.
 * - Otherwise the top-up row is locked FOR UPDATE, the wallet is locked and
 *   credited, a balanced journal (PAYMOB_CLEARING -> WALLET) is appended in the
 *   same transaction, and the unique constraint on the Paymob transaction id is
 *   the last line of defense against a double credit.
 */
public class HandlePaymobWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymobWebhookUseCase.class);

    private final TopUpRepository topUpRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public HandlePaymobWebhookUseCase(TopUpRepository topUpRepository,
                                      WalletRepository walletRepository,
                                      LedgerEntryRepository ledgerEntryRepository,
                                      UserRepository userRepository,
                                      NotificationService notificationService,
                                      TransactionRunner transactionRunner,
                                      Clock clock) {
        this.topUpRepository = topUpRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public WebhookProcessingResult execute(PaymobTransactionCallback callback) {
        try {
            return transactionRunner.inTransaction(() -> process(callback));
        } catch (DataIntegrityViolationException duplicateTransaction) {
            // Two webhooks for the same transaction raced; the unique constraint won.
            return WebhookProcessingResult.ALREADY_PROCESSED;
        }
    }

    private WebhookProcessingResult process(PaymobTransactionCallback callback) {
        if (topUpRepository.existsByPaymobTransactionId(callback.transactionId())) {
            return WebhookProcessingResult.ALREADY_PROCESSED;
        }

        var locked = topUpRepository.findByMerchantOrderIdForUpdate(callback.merchantOrderId());
        if (locked.isEmpty()) {
            log.warn("Webhook for unknown merchant order {}", callback.merchantOrderId());
            return WebhookProcessingResult.UNKNOWN_ORDER;
        }
        TopUp topUp = locked.get();

        if (topUp.status() != com.wallet.payment.domain.TopUpStatus.PENDING) {
            return WebhookProcessingResult.ALREADY_PROCESSED;
        }

        if (!callback.settled()) {
            // Declined, pending, errored, voided or refunded: no money was captured for
            // this attempt, so the top-up stays PENDING - the customer can retry on the
            // same payment page and a later successful callback still credits them.
            // The expire job closes abandoned top-ups.
            log.info("Unsettled payment for top-up {} (success={}, pending={}, error={}, voided={}, refunded={})",
                    topUp.merchantOrderId(), callback.success(), callback.pending(),
                    callback.errorOccured(), callback.isVoided(), callback.isRefunded());
            return WebhookProcessingResult.PAYMENT_FAILED;
        }

        if (callback.amountCents() != topUp.amountMinor()
                || !topUp.currency().equalsIgnoreCase(callback.currency())) {
            // The customer WAS charged something other than what we recorded - never
            // credit silently, park it for a human.
            log.error("Top-up {} needs review: expected {} {}, webhook says {} {}",
                    topUp.merchantOrderId(), topUp.amountMinor(), topUp.currency(),
                    callback.amountCents(), callback.currency());
            topUpRepository.save(topUp.markNeedsReview(callback.transactionId()));
            return WebhookProcessingResult.NEEDS_REVIEW;
        }

        Instant now = clock.instant();
        Wallet wallet = walletRepository.findByUserIdForUpdate(topUp.userId())
                .orElseThrow(() -> new IllegalStateException("Wallet missing for user " + topUp.userId()));
        Wallet credited = wallet.credit(topUp.amountMinor());
        walletRepository.save(credited);

        String journalId = UUID.randomUUID().toString();
        String description = "Top-up via Paymob, transaction " + callback.transactionId();
        LedgerJournal journal = new LedgerJournal(journalId, List.of(
                LedgerEntry.debit(journalId, LedgerAccount.PAYMOB_CLEARING, topUp.amountMinor(), description, now),
                LedgerEntry.credit(journalId, LedgerAccount.ofWallet(wallet.id()), topUp.amountMinor(), description, now)));
        ledgerEntryRepository.saveAll(journal);

        topUpRepository.save(topUp.markSucceeded(callback.transactionId(), journalId));

        userRepository.findById(topUp.userId()).ifPresent(user ->
                notificationService.sendTopUpReceipt(user.email(), user.fullName(),
                        topUp.amountMinor(), topUp.currency(), credited.balanceMinor()));

        return WebhookProcessingResult.CREDITED;
    }
}
