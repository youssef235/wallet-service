package com.wallet.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpReversal;
import com.wallet.payment.domain.TopUpReversalRepository;
import com.wallet.payment.domain.TopUpStatus;
import com.wallet.support.FakeTransactionRunner;
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
import org.springframework.dao.DataIntegrityViolationException;

class HandlePaymobReversalUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final TopUpRepository topUpRepository = mock(TopUpRepository.class);
    private final TopUpReversalRepository topUpReversalRepository = mock(TopUpReversalRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);

    private HandlePaymobReversalUseCase useCase;

    private TopUp successfulTopUp;

    @BeforeEach
    void setUp() {
        useCase = new HandlePaymobReversalUseCase(topUpRepository, topUpReversalRepository,
                walletRepository, ledgerEntryRepository, new FakeTransactionRunner(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        successfulTopUp = TopUp.reconstitute(5L, 1L, 10_000, "EGP", TopUpStatus.SUCCESS,
                "WTU-1-abc", 555L, 999L, "journal-1", NOW);
        when(topUpRepository.findByPaymobOrderIdForUpdate(555L))
                .thenReturn(Optional.of(successfulTopUp));
        when(walletRepository.findByUserIdForUpdate(1L))
                .thenReturn(Optional.of(Wallet.reconstitute(11L, 1L, 15_000, "EGP", NOW)));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(topUpReversalRepository.save(any(TopUpReversal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fullRefundDebitsTheWalletWithAMirrorJournalAndMarksRefunded() {
        when(topUpReversalRepository.sumAmountByTopupId(5L)).thenReturn(10_000L);

        var result = useCase.execute(1001L, 555L, 999L, 10_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.REVERSED);

        var walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().balanceMinor()).isEqualTo(5_000);

        var journalCaptor = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(ledgerEntryRepository).saveAll(journalCaptor.capture());
        assertThat(journalCaptor.getValue().entries()).hasSize(2);
        assertThat(journalCaptor.getValue().entries().stream().mapToLong(e -> e.amountMinor()).sum()).isZero();

        var topUpCaptor = ArgumentCaptor.forClass(TopUp.class);
        verify(topUpRepository).save(topUpCaptor.capture());
        assertThat(topUpCaptor.getValue().status()).isEqualTo(TopUpStatus.REFUNDED);
    }

    @Test
    void aPartialRefundAppliesButKeepsTheTopUpSuccessful() {
        when(topUpReversalRepository.sumAmountByTopupId(5L)).thenReturn(4_000L);

        var result = useCase.execute(1001L, 555L, 999L, 4_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.REVERSED);
        verify(topUpReversalRepository).save(any(TopUpReversal.class));
        var walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().balanceMinor()).isEqualTo(11_000);
        // Not fully reversed: the top-up is not marked REFUNDED.
        verify(topUpRepository, never()).save(any());
    }

    @Test
    void aSecondPartialRefundAppliesAndCompletesTheRefund() {
        when(topUpReversalRepository.sumAmountByTopupId(5L)).thenReturn(10_000L);

        var result = useCase.execute(1002L, 555L, 999L, 6_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.REVERSED);
        var topUpCaptor = ArgumentCaptor.forClass(TopUp.class);
        verify(topUpRepository).save(topUpCaptor.capture());
        assertThat(topUpCaptor.getValue().status()).isEqualTo(TopUpStatus.REFUNDED);
    }

    @Test
    void theSameReversalNeverAppliesTwice() {
        when(topUpReversalRepository.save(any(TopUpReversal.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate reversal_transaction_id"));

        var result = useCase.execute(1001L, 555L, 999L, 10_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.ALREADY_PROCESSED);
        verify(walletRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void aReversalOnAFullyRefundedTopUpIsIgnored() {
        TopUp alreadyRefunded = TopUp.reconstitute(5L, 1L, 10_000, "EGP", TopUpStatus.REFUNDED,
                "WTU-1-abc", 555L, 999L, "journal-1", NOW);
        when(topUpRepository.findByPaymobOrderIdForUpdate(555L))
                .thenReturn(Optional.of(alreadyRefunded));

        var result = useCase.execute(1002L, 555L, 999L, 1_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.IGNORED);
        verify(topUpReversalRepository, never()).save(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void refundMayDriveTheBalanceNegative() {
        when(walletRepository.findByUserIdForUpdate(1L))
                .thenReturn(Optional.of(Wallet.reconstitute(11L, 1L, 3_000, "EGP", NOW)));
        when(topUpReversalRepository.sumAmountByTopupId(5L)).thenReturn(10_000L);

        var result = useCase.execute(1001L, 555L, 999L, 10_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.REVERSED);
        var walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().balanceMinor()).isEqualTo(-7_000);
    }

    @Test
    void reversalOfANonSuccessfulTopUpIsIgnored() {
        TopUp pending = TopUp.reconstitute(5L, 1L, 10_000, "EGP", TopUpStatus.PENDING,
                "WTU-1-abc", 555L, null, null, NOW);
        when(topUpRepository.findByPaymobOrderIdForUpdate(555L)).thenReturn(Optional.of(pending));

        var result = useCase.execute(1001L, 555L, 999L, 10_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.IGNORED);
        verify(walletRepository, never()).save(any());
    }

    @Test
    void unknownOrderIsReported() {
        when(topUpRepository.findByPaymobOrderIdForUpdate(555L)).thenReturn(Optional.empty());

        var result = useCase.execute(1001L, 555L, 999L, 10_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.UNKNOWN_TRANSACTION);
    }

    @Test
    void fallsBackToTheParentTransactionWhenNoOrderIdIsSigned() {
        when(topUpRepository.findByPaymobTransactionIdForUpdate(999L))
                .thenReturn(Optional.of(successfulTopUp));
        when(topUpReversalRepository.sumAmountByTopupId(5L)).thenReturn(10_000L);

        var result = useCase.execute(1001L, null, 999L, 10_000);

        assertThat(result).isEqualTo(ReversalProcessingResult.REVERSED);
    }
}
