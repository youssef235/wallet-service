package com.wallet.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallet.payment.domain.PaymentGateway;
import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpStatus;
import com.wallet.support.FakeTransactionRunner;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import com.wallet.user.domain.UserRole;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StartTopUpUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final TopUpRepository topUpRepository = mock(TopUpRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final PaymentGateway paymentGateway = mock(PaymentGateway.class);

    private StartTopUpUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new StartTopUpUseCase(topUpRepository, userRepository, walletRepository,
                paymentGateway, new FakeTransactionRunner(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.reconstitute(1L, "a@example.com", "Ahmed", "hash", UserRole.USER, NOW)));
        when(walletRepository.findByUserId(1L)).thenReturn(
                Optional.of(Wallet.reconstitute(11L, 1L, 0, "EGP", NOW)));
        when(topUpRepository.save(any(TopUp.class))).thenAnswer(inv -> {
            TopUp t = inv.getArgument(0);
            return TopUp.reconstitute(99L, t.userId(), t.amountMinor(), t.currency(), t.status(),
                    t.merchantOrderId(), t.paymobOrderId(), t.paymobTransactionId(), t.journalId(), t.createdAt());
        });
    }

    @Test
    void successfulStartStoresPendingThenAttachesProviderOrder() {
        when(paymentGateway.createPaymentIntent(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), anyString()))
                .thenReturn(new PaymentGateway.PaymentIntent(555L, "https://pay/iframe"));

        StartTopUpResult result = useCase.execute(new StartTopUpCommand(1L, 10_000));

        assertThat(result.paymentUrl()).isEqualTo("https://pay/iframe");
        ArgumentCaptor<TopUp> captor = ArgumentCaptor.forClass(TopUp.class);
        verify(topUpRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).paymobOrderId()).isEqualTo(555L);
    }

    @Test
    void gatewayFailureMarksTheTopUpFailedAndPropagates() {
        when(paymentGateway.createPaymentIntent(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), anyString()))
                .thenThrow(new IllegalStateException("Paymob unreachable"));

        assertThatThrownBy(() -> useCase.execute(new StartTopUpCommand(1L, 10_000)))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<TopUp> captor = ArgumentCaptor.forClass(TopUp.class);
        verify(topUpRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(TopUpStatus.FAILED);
    }
}
