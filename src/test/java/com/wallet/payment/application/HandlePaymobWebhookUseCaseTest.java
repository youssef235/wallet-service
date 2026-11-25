package com.wallet.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.support.FakeTransactionRunner;
import com.wallet.support.NoOpNotificationService;
import com.wallet.user.domain.UserRepository;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.LedgerJournal;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HandlePaymobWebhookUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-25T20:00:00Z");

    private final TopUpRepository topUpRepository = mock(TopUpRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private HandlePaymobWebhookUseCase useCase;

    private final TopUp pendingTopUp = TopUp.reconstitute(5L, 1L, 10_000, "EGP",
            com.wallet.payment.domain.TopUpStatus.PENDING, "WTU-1-abc", 555L, null, null, null, NOW);

    @BeforeEach
    void setUp() {
        useCase = new HandlePaymobWebhookUseCase(topUpRepository, walletRepository,
                ledgerEntryRepository, userRepository, new NoOpNotificationService(),
                new FakeTransactionRunner(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void successfulWebhookCreditsWalletOnce() {
        when(topUpRepository.existsByPaymobTransactionId(999L)).thenReturn(false);
        when(topUpRepository.findByMerchantOrderIdForUpdate("WTU-1-abc"))
                .thenReturn(Optional.of(pendingTopUp));
        when(walletRepository.findByUserIdForUpdate(1L))
                .thenReturn(Optional.of(Wallet.reconstitute(11L, 1L, 0, "EGP", NOW)));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        var result = useCase.execute(new PaymobTransactionCallback(999L, "WTU-1-abc", 10_000, "EGP", true, false, false, false, false));

        assertThat(result).isEqualTo(WebhookProcessingResult.CREDITED);

        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().balanceMinor()).isEqualTo(10_000);

        ArgumentCaptor<LedgerJournal> journalCaptor = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(ledgerEntryRepository).saveAll(journalCaptor.capture());
        assertThat(journalCaptor.getValue().entries().stream().mapToLong(e -> e.amountMinor()).sum()).isZero();

        ArgumentCaptor<TopUp> topUpCaptor = ArgumentCaptor.forClass(TopUp.class);
        verify(topUpRepository).save(topUpCaptor.capture());
        assertThat(topUpCaptor.getValue().status()).isEqualTo(com.wallet.payment.domain.TopUpStatus.SUCCESS);
        assertThat(topUpCaptor.getValue().paymobTransactionId()).isEqualTo(999L);
    }

    @Test
    void retriedWebhookWithSameTransactionIdIsIgnored() {
        when(topUpRepository.existsByPaymobTransactionId(999L)).thenReturn(true);

        var result = useCase.execute(new PaymobTransactionCallback(999L, "WTU-1-abc", 10_000, "EGP", true, false, false, false, false));

        assertThat(result).isEqualTo(WebhookProcessingResult.ALREADY_PROCESSED);
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void alreadySettledTopUpIsNotCreditedAgain() {
        TopUp succeeded = pendingTopUp.markSucceeded(999L, "journal-1");
        when(topUpRepository.existsByPaymobTransactionId(888L)).thenReturn(false);
        when(topUpRepository.findByMerchantOrderIdForUpdate("WTU-1-abc"))
                .thenReturn(Optional.of(succeeded));

        var result = useCase.execute(new PaymobTransactionCallback(888L, "WTU-1-abc", 10_000, "EGP", true, false, false, false, false));

        assertThat(result).isEqualTo(WebhookProcessingResult.ALREADY_PROCESSED);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void failedPaymentLeavesTopUpPendingForRetryAndNeverCredits() {
        when(topUpRepository.existsByPaymobTransactionId(999L)).thenReturn(false);
        when(topUpRepository.findByMerchantOrderIdForUpdate("WTU-1-abc"))
                .thenReturn(Optional.of(pendingTopUp));

        var result = useCase.execute(new PaymobTransactionCallback(999L, "WTU-1-abc", 10_000, "EGP", false, false, false, false, false));

        assertThat(result).isEqualTo(WebhookProcessingResult.PAYMENT_FAILED);
        // No state change: a later successful callback for the same order must still credit.
        verify(topUpRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void amountMismatchParksTheTopUpForReviewAndNeverCredits() {
        when(topUpRepository.existsByPaymobTransactionId(999L)).thenReturn(false);
        when(topUpRepository.findByMerchantOrderIdForUpdate("WTU-1-abc"))
                .thenReturn(Optional.of(pendingTopUp));

        var result = useCase.execute(new PaymobTransactionCallback(999L, "WTU-1-abc", 5_000, "EGP", true, false, false, false, false));

        assertThat(result).isEqualTo(WebhookProcessingResult.NEEDS_REVIEW);
        var captor = ArgumentCaptor.forClass(TopUp.class);
        verify(topUpRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(com.wallet.payment.domain.TopUpStatus.NEEDS_REVIEW);
        assertThat(captor.getValue().paymobTransactionId()).isEqualTo(999L);
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void successfulWebhookAfterAFailedAttemptStillCredits() {
        when(topUpRepository.existsByPaymobTransactionId(999L)).thenReturn(false);
        when(topUpRepository.findByMerchantOrderIdForUpdate("WTU-1-abc"))
                .thenReturn(Optional.of(pendingTopUp));
        when(walletRepository.findByUserIdForUpdate(1L))
                .thenReturn(Optional.of(Wallet.reconstitute(11L, 1L, 0, "EGP", NOW)));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        var failed = useCase.execute(new PaymobTransactionCallback(998L, "WTU-1-abc", 10_000, "EGP", false, false, false, false, false));
        var settled = useCase.execute(new PaymobTransactionCallback(999L, "WTU-1-abc", 10_000, "EGP", true, false, false, false, false));

        assertThat(failed).isEqualTo(WebhookProcessingResult.PAYMENT_FAILED);
        assertThat(settled).isEqualTo(WebhookProcessingResult.CREDITED);
    }

    @Test
    void unknownOrderIsReported() {
        when(topUpRepository.existsByPaymobTransactionId(999L)).thenReturn(false);
        when(topUpRepository.findByMerchantOrderIdForUpdate("WTU-1-xyz")).thenReturn(Optional.empty());

        var result = useCase.execute(new PaymobTransactionCallback(999L, "WTU-1-xyz", 10_000, "EGP", true, false, false, false, false));

        assertThat(result).isEqualTo(WebhookProcessingResult.UNKNOWN_ORDER);
    }
}
