package com.wallet.config;

import com.wallet.notification.application.NotificationService;
import com.wallet.payment.application.ExpireStaleTopUpsUseCase;
import com.wallet.payment.application.HandlePaymobReversalUseCase;
import com.wallet.payment.application.HandlePaymobWebhookUseCase;
import com.wallet.payment.application.ListMyTopUpsUseCase;
import com.wallet.payment.application.StartTopUpUseCase;
import com.wallet.payment.domain.PaymentGateway;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpReversalRepository;
import com.wallet.recon.application.ReconcileWalletsUseCase;
import com.wallet.shared.application.TransactionRunner;
import com.wallet.user.application.LoginAttemptGuard;
import com.wallet.user.application.LoginUserUseCase;
import com.wallet.user.application.LogoutUseCase;
import com.wallet.user.application.PasswordHasher;
import com.wallet.user.application.RefreshSessionUseCase;
import com.wallet.user.application.RegisterUserUseCase;
import com.wallet.user.application.TokenHasher;
import com.wallet.user.application.TokenService;
import com.wallet.user.domain.RefreshTokenRepository;
import com.wallet.user.domain.UserRepository;
import com.wallet.wallet.application.GetMyWalletUseCase;
import com.wallet.wallet.application.TransactionHistoryUseCase;
import com.wallet.wallet.application.TransferUseCase;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.TransferRepository;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use cases are pure Java: they are wired explicitly here instead of being
 * annotated with Spring stereotypes, keeping the application layer framework-free.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public RegisterUserUseCase registerUserUseCase(
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher, TokenHasher tokenHasher, TokenService tokenService,
            WalletRepository walletRepository, TransactionRunner transactionRunner, Clock clock,
            @Value("${app.jwt.refresh-expiration-days}") long refreshDays,
            @Value("${app.wallet.default-currency:EGP}") String defaultCurrency) {
        return new RegisterUserUseCase(userRepository, refreshTokenRepository, passwordHasher,
                tokenHasher, tokenService, walletRepository, transactionRunner, clock,
                Duration.ofDays(refreshDays), defaultCurrency);
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher, TokenHasher tokenHasher, TokenService tokenService,
            TransactionRunner transactionRunner, LoginAttemptGuard loginAttemptGuard, Clock clock,
            @Value("${app.jwt.refresh-expiration-days}") long refreshDays) {
        return new LoginUserUseCase(userRepository, refreshTokenRepository, passwordHasher,
                tokenHasher, tokenService, transactionRunner, loginAttemptGuard,
                clock, Duration.ofDays(refreshDays));
    }

    @Bean
    public LoginAttemptGuard loginAttemptGuard(Clock clock) {
        return new LoginAttemptGuard(clock);
    }

    @Bean
    public RefreshSessionUseCase refreshSessionUseCase(
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            TokenHasher tokenHasher, TokenService tokenService,
            TransactionRunner transactionRunner, Clock clock,
            @Value("${app.jwt.refresh-expiration-days}") long refreshDays) {
        return new RefreshSessionUseCase(userRepository, refreshTokenRepository, tokenHasher,
                tokenService, transactionRunner, clock, Duration.ofDays(refreshDays));
    }

    @Bean
    public LogoutUseCase logoutUseCase(RefreshTokenRepository refreshTokenRepository,
                                       TokenHasher tokenHasher,
                                       TransactionRunner transactionRunner) {
        return new LogoutUseCase(refreshTokenRepository, tokenHasher, transactionRunner);
    }

    @Bean
    public GetMyWalletUseCase getMyWalletUseCase(
            WalletRepository walletRepository, TransactionRunner transactionRunner) {
        return new GetMyWalletUseCase(walletRepository, transactionRunner);
    }

    @Bean
    public TransferUseCase transferUseCase(
            WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository,
            TransferRepository transferRepository, UserRepository userRepository,
            NotificationService notificationService, TransactionRunner transactionRunner, Clock clock) {
        return new TransferUseCase(walletRepository, ledgerEntryRepository, transferRepository,
                userRepository, notificationService, transactionRunner, clock);
    }

    @Bean
    public TransactionHistoryUseCase transactionHistoryUseCase(
            WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository,
            TransactionRunner transactionRunner) {
        return new TransactionHistoryUseCase(walletRepository, ledgerEntryRepository, transactionRunner);
    }

    @Bean
    public StartTopUpUseCase startTopUpUseCase(
            TopUpRepository topUpRepository, UserRepository userRepository,
            WalletRepository walletRepository, PaymentGateway paymentGateway,
            TransactionRunner transactionRunner, Clock clock) {
        return new StartTopUpUseCase(topUpRepository, userRepository, walletRepository,
                paymentGateway, transactionRunner, clock);
    }

    @Bean
    public HandlePaymobWebhookUseCase handlePaymobWebhookUseCase(
            TopUpRepository topUpRepository, WalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository, UserRepository userRepository,
            NotificationService notificationService, TransactionRunner transactionRunner, Clock clock) {
        return new HandlePaymobWebhookUseCase(topUpRepository, walletRepository, ledgerEntryRepository,
                userRepository, notificationService, transactionRunner, clock);
    }

    @Bean
    public HandlePaymobReversalUseCase handlePaymobReversalUseCase(
            TopUpRepository topUpRepository, TopUpReversalRepository topUpReversalRepository,
            WalletRepository walletRepository,
            LedgerEntryRepository ledgerEntryRepository, TransactionRunner transactionRunner, Clock clock) {
        return new HandlePaymobReversalUseCase(topUpRepository, topUpReversalRepository, walletRepository,
                ledgerEntryRepository, transactionRunner, clock);
    }

    @Bean
    public ListMyTopUpsUseCase listMyTopUpsUseCase(TopUpRepository topUpRepository,
                                                   TransactionRunner transactionRunner) {
        return new ListMyTopUpsUseCase(topUpRepository, transactionRunner);
    }

    @Bean
    public ExpireStaleTopUpsUseCase expireStaleTopUpsUseCase(
            TopUpRepository topUpRepository, TransactionRunner transactionRunner, Clock clock,
            @Value("${app.paymob.topup-max-age-hours:24}") long maxAgeHours) {
        return new ExpireStaleTopUpsUseCase(topUpRepository, transactionRunner, clock,
                Duration.ofHours(maxAgeHours));
    }

    @Bean
    public ReconcileWalletsUseCase reconcileWalletsUseCase(
            WalletRepository walletRepository, LedgerEntryRepository ledgerEntryRepository,
            TransactionRunner transactionRunner) {
        return new ReconcileWalletsUseCase(walletRepository, ledgerEntryRepository, transactionRunner);
    }
}
