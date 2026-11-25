package com.wallet.wallet.application;

import com.wallet.notification.application.NotificationService;
import com.wallet.shared.application.TransactionRunner;
import com.wallet.shared.domain.ConflictException;
import com.wallet.shared.domain.NotFoundException;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import com.wallet.wallet.domain.LedgerAccount;
import com.wallet.wallet.domain.LedgerEntry;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.LedgerJournal;
import com.wallet.wallet.domain.SelfTransferException;
import com.wallet.wallet.domain.Transfer;
import com.wallet.wallet.domain.TransferRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Moves money between two wallets safely:
 *
 * 1. Idempotency: the client sends an idempotency key; a retried request replays
 *    the stored result instead of debiting twice (backed by a unique constraint).
 * 2. Locking: both wallet rows are locked FOR UPDATE in ascending user-id order,
 *    so two opposite transfers (A->B and B->A) cannot deadlock.
 * 3. Double-entry: the balance change is written together with a balanced journal
 *    in the same transaction; either both happen or neither does.
 */
public class TransferUseCase {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public TransferUseCase(WalletRepository walletRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           TransferRepository transferRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           TransactionRunner transactionRunner,
                           Clock clock) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public TransferResult execute(TransferCommand command) {
        Instant now = clock.instant();
        try {
            return transactionRunner.inTransaction(() -> doTransfer(command, now));
        } catch (DataIntegrityViolationException duplicateKey) {
            // Lost the race against an identical concurrent request: replay its stored result.
            return transactionRunner.inTransaction(() -> {
                Wallet senderWallet = walletRepository.findByUserId(command.senderUserId())
                        .orElseThrow(() -> duplicateKey);
                return transferRepository.findByFromWalletIdAndIdempotencyKey(
                                senderWallet.id(), command.idempotencyKey())
                        .map(stored -> replayResult(command, stored))
                        .orElseThrow(() -> duplicateKey);
            });
        }
    }

    private TransferResult doTransfer(TransferCommand command, Instant now) {
        User sender = userRepository.findById(command.senderUserId())
                .orElseThrow(() -> new NotFoundException("Sender not found"));
        User recipient = userRepository.findByEmail(command.recipientEmail().trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Recipient not found"));
        if (sender.id().equals(recipient.id())) {
            throw new SelfTransferException();
        }

        // Lock both wallets in ascending user-id order: a fixed global order prevents deadlocks.
        Wallet first = lockWallet(sender.id(), recipient.id(), true);
        Wallet second = lockWallet(sender.id(), recipient.id(), false);
        Wallet senderWallet = first.userId().equals(sender.id()) ? first : second;
        Wallet recipientWallet = first.userId().equals(recipient.id()) ? first : second;

        // Idempotency check after locking: serialized per sender wallet, and it must not
        // read the wallet without a lock first (a cached unlocked entity would shadow the
        // locked read and lose concurrent balance updates).
        var existing = transferRepository.findByFromWalletIdAndIdempotencyKey(
                senderWallet.id(), command.idempotencyKey());
        if (existing.isPresent()) {
            return replayResult(command, existing.get());
        }

        Wallet senderAfter = senderWallet.debit(command.amountMinor());
        Wallet recipientAfter = recipientWallet.credit(command.amountMinor());
        walletRepository.save(senderAfter);
        walletRepository.save(recipientAfter);

        String journalId = UUID.randomUUID().toString();
        // Neutral wording: the ledger is immutable, so no personal data (emails)
        // is stored in it. The counterparty is recoverable via the transfers table.
        String description = "P2P transfer";
        LedgerJournal journal = new LedgerJournal(journalId, java.util.List.of(
                LedgerEntry.debit(journalId, LedgerAccount.ofWallet(senderWallet.id()), command.amountMinor(), description, now),
                LedgerEntry.credit(journalId, LedgerAccount.ofWallet(recipientWallet.id()), command.amountMinor(), description, now)));
        ledgerEntryRepository.saveAll(journal);

        Transfer transfer = Transfer.of(command.idempotencyKey(), senderWallet.id(), recipientWallet.id(),
                command.amountMinor(), journalId, now);
        transferRepository.save(transfer);

        notificationService.sendTransferReceived(recipient.email(), recipient.fullName(),
                command.amountMinor(), senderAfter.currency(), sender.fullName(), recipientAfter.balanceMinor());

        return new TransferResult(command.idempotencyKey(), command.amountMinor(), senderAfter.currency(),
                senderAfter.balanceMinor(), recipient.email(), now, false);
    }

    private Wallet lockWallet(Long senderUserId, Long recipientUserId, boolean firstLock) {
        Long userIdToLock = firstLock
                ? Math.min(senderUserId, recipientUserId)
                : Math.max(senderUserId, recipientUserId);
        return walletRepository.findByUserIdForUpdate(userIdToLock)
                .orElseThrow(() -> new NotFoundException(
                        "Wallet not found for user " + userIdToLock + " (wallets open at registration)"));
    }

    /** Replays the stored outcome of a request that already ran with this idempotency key. */
    private TransferResult replayResult(TransferCommand command, Transfer stored) {
        if (stored.amountMinor() != command.amountMinor()) {
            throw new ConflictException("Idempotency key reused with a different request");
        }
        Wallet senderWallet = walletRepository.findByUserId(command.senderUserId())
                .orElseThrow(() -> new NotFoundException("Wallet not found for user " + command.senderUserId()));
        User recipient = userRepository.findByEmail(command.recipientEmail().trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("Recipient not found"));
        Wallet recipientWallet = walletRepository.findByUserId(recipient.id())
                .orElseThrow(() -> new ConflictException("Idempotency key reused with a different request"));
        if (!stored.toWalletId().equals(recipientWallet.id())) {
            throw new ConflictException("Idempotency key reused with a different request");
        }
        return new TransferResult(stored.idempotencyKey(), stored.amountMinor(), senderWallet.currency(),
                senderWallet.balanceMinor(), recipient.email(), stored.createdAt(), true);
    }
}
