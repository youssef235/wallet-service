package com.wallet.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpStatus;
import com.wallet.support.TestcontainersConfiguration;
import com.wallet.user.application.RegisterUserCommand;
import com.wallet.user.application.RegisterUserUseCase;
import com.wallet.wallet.domain.WalletRepository;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TopUpLifecycleIntegrationTest {

    @Autowired
    private RegisterUserUseCase registerUser;
    @Autowired
    private StartTopUpUseCase startTopUp;
    @Autowired
    private HandlePaymobWebhookUseCase webhook;
    @Autowired
    private HandlePaymobReversalUseCase reversal;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TopUpRepository topUpRepository;

    private Long newUserId() {
        return registerUser.execute(new RegisterUserCommand(
                "topup-" + UUID.randomUUID() + "@example.com", "Top Up Test", "password-123")).user().id();
    }

    private static long uniqueId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
    }

    private long balanceOf(Long userId) {
        return walletRepository.findByUserId(userId).orElseThrow().balanceMinor();
    }

    private TopUpStatus statusOf(StartTopUpResult top) {
        return topUpRepository.findByMerchantOrderId(top.merchantOrderId()).orElseThrow().status();
    }

    private static PaymobTransactionCallback callback(StartTopUpResult top, long txId, long amount, boolean success) {
        return new PaymobTransactionCallback(txId, top.merchantOrderId(), amount, top.currency(),
                success, false, false, false, false);
    }

    @Test
    void failedAttemptThenSuccessStillCreditsExactlyOnce() {
        Long userId = newUserId();
        StartTopUpResult top = startTopUp.execute(new StartTopUpCommand(userId, 5_000));

        assertThat(webhook.execute(callback(top, uniqueId(), 5_000, false)))
                .isEqualTo(WebhookProcessingResult.PAYMENT_FAILED);
        assertThat(statusOf(top)).isEqualTo(TopUpStatus.PENDING);

        long txId = uniqueId();
        assertThat(webhook.execute(callback(top, txId, 5_000, true))).isEqualTo(WebhookProcessingResult.CREDITED);
        assertThat(balanceOf(userId)).isEqualTo(5_000);

        // Paymob retries the same callback.
        assertThat(webhook.execute(callback(top, txId, 5_000, true)))
                .isEqualTo(WebhookProcessingResult.ALREADY_PROCESSED);
        assertThat(balanceOf(userId)).isEqualTo(5_000);
    }

    @Test
    void amountMismatchIsParkedForReviewAndNeverCredited() {
        Long userId = newUserId();
        StartTopUpResult top = startTopUp.execute(new StartTopUpCommand(userId, 5_000));

        assertThat(webhook.execute(callback(top, uniqueId(), 4_000, true)))
                .isEqualTo(WebhookProcessingResult.NEEDS_REVIEW);
        assertThat(balanceOf(userId)).isZero();
        assertThat(statusOf(top)).isEqualTo(TopUpStatus.NEEDS_REVIEW);
    }

    @Test
    void partialRefundsAddUpAndTheLastOneMarksRefunded() {
        Long userId = newUserId();
        StartTopUpResult top = startTopUp.execute(new StartTopUpCommand(userId, 10_000));
        long originalTx = uniqueId();
        webhook.execute(callback(top, originalTx, 10_000, true));

        // StubPaymentGateway (used when app.paymob.enabled=false) gives order id 10_000_000 + topUpId.
        long paymobOrderId = 10_000_000L + top.topUpId();
        long refund1 = uniqueId();
        long refund2 = uniqueId();

        assertThat(reversal.execute(refund1, paymobOrderId, originalTx, 4_000))
                .isEqualTo(ReversalProcessingResult.REVERSED);
        assertThat(balanceOf(userId)).isEqualTo(6_000);
        assertThat(statusOf(top)).isEqualTo(TopUpStatus.SUCCESS);

        // Paymob retries the first refund callback: must not debit twice.
        assertThat(reversal.execute(refund1, paymobOrderId, originalTx, 4_000))
                .isEqualTo(ReversalProcessingResult.ALREADY_PROCESSED);
        assertThat(balanceOf(userId)).isEqualTo(6_000);

        assertThat(reversal.execute(refund2, paymobOrderId, originalTx, 6_000))
                .isEqualTo(ReversalProcessingResult.REVERSED);
        assertThat(balanceOf(userId)).isZero();
        assertThat(statusOf(top)).isEqualTo(TopUpStatus.REFUNDED);
    }
}
