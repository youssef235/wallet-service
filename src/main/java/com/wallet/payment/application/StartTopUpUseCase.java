package com.wallet.payment.application;

import com.wallet.payment.domain.PaymentGateway;
import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.shared.application.TransactionRunner;
import com.wallet.shared.domain.NotFoundException;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Starts a top-up: stores a PENDING top-up, asks Paymob for a payment page URL,
 * and returns that URL so the user can pay. The wallet is credited later,
 * when the HMAC-verified webhook arrives.
 */
public class StartTopUpUseCase {

    private final TopUpRepository topUpRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public StartTopUpUseCase(TopUpRepository topUpRepository,
                             UserRepository userRepository,
                             WalletRepository walletRepository,
                             PaymentGateway paymentGateway,
                             TransactionRunner transactionRunner,
                             Clock clock) {
        this.topUpRepository = topUpRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.paymentGateway = paymentGateway;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public StartTopUpResult execute(StartTopUpCommand command) {
        Instant now = clock.instant();
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Step 1: record the PENDING top-up so the webhook can match it later.
        TopUp saved = transactionRunner.inTransaction(() -> {
            Wallet wallet = walletRepository.findByUserId(command.userId())
                    .orElseThrow(() -> new NotFoundException(
                            "Wallet not found for user " + command.userId() + " (wallets open at registration)"));
            String merchantOrderId = "WTU-" + command.userId() + "-" + UUID.randomUUID();
            return topUpRepository.save(TopUp.pending(
                    command.userId(), command.amountMinor(), wallet.currency(), merchantOrderId, now));
        });

        // Step 2: call Paymob outside any transaction - slow HTTP must never hold a DB
        // connection or lock. If the gateway fails, close the top-up as FAILED.
        PaymentGateway.PaymentIntent intent;
        try {
            intent = paymentGateway.createPaymentIntent(
                    saved.id(), saved.merchantOrderId(), saved.amountMinor(), saved.currency(),
                    user.email(), user.fullName());
        } catch (RuntimeException gatewayFailure) {
            transactionRunner.inTransaction(() -> topUpRepository.save(saved.markFailed()));
            throw gatewayFailure;
        }

        // Step 3: attach the provider order id in its own short transaction.
        transactionRunner.inTransaction(() ->
                topUpRepository.save(saved.withPaymobOrder(intent.providerOrderId())));

        return new StartTopUpResult(saved.id(), saved.merchantOrderId(), saved.amountMinor(),
                saved.currency(), intent.paymentUrl());
    }
}
