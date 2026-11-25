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
    private final String defaultCurrency;

    public StartTopUpUseCase(TopUpRepository topUpRepository,
                             UserRepository userRepository,
                             WalletRepository walletRepository,
                             PaymentGateway paymentGateway,
                             TransactionRunner transactionRunner,
                             Clock clock,
                             String defaultCurrency) {
        this.topUpRepository = topUpRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.paymentGateway = paymentGateway;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.defaultCurrency = defaultCurrency;
    }

    public StartTopUpResult execute(StartTopUpCommand command) {
        return transactionRunner.inTransaction(() -> {
            Instant now = clock.instant();
            User user = userRepository.findById(command.userId())
                    .orElseThrow(() -> new NotFoundException("User not found"));
            Wallet wallet = walletRepository.findByUserId(command.userId())
                    .orElseGet(() -> walletRepository.save(
                            Wallet.openFor(command.userId(), defaultCurrency, now)));

            String merchantOrderId = "WTU-" + command.userId() + "-" + UUID.randomUUID();
            TopUp saved = topUpRepository.save(
                    TopUp.pending(command.userId(), command.amountMinor(), wallet.currency(), merchantOrderId, now));

            PaymentGateway.PaymentIntent intent = paymentGateway.createPaymentIntent(
                    saved.id(), merchantOrderId, saved.amountMinor(), saved.currency(),
                    user.email(), user.fullName());
            topUpRepository.save(saved.withPaymobOrder(intent.providerOrderId()));

            return new StartTopUpResult(saved.id(), merchantOrderId, saved.amountMinor(),
                    saved.currency(), intent.paymentUrl());
        });
    }
}
