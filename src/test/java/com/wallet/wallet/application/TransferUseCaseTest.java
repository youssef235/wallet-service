package com.wallet.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallet.support.FakeTransactionRunner;
import com.wallet.support.NoOpNotificationService;
import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import com.wallet.user.domain.UserRole;
import com.wallet.wallet.domain.InsufficientFundsException;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.LedgerJournal;
import com.wallet.wallet.domain.SelfTransferException;
import com.wallet.wallet.domain.Transfer;
import com.wallet.shared.domain.ConflictException;
import com.wallet.wallet.domain.TransferRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransferUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-25T20:00:00Z");

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private TransferUseCase useCase;

    private final User sender = User.reconstitute(1L, "ahmed@example.com", "Ahmed Ali", "hash",
            UserRole.USER, NOW);
    private final User recipient = User.reconstitute(2L, "sara@example.com", "Sara Samy", "hash",
            UserRole.USER, NOW);

    @BeforeEach
    void setUp() {
        useCase = new TransferUseCase(walletRepository, ledgerEntryRepository, transferRepository,
                userRepository, new NoOpNotificationService(), new FakeTransactionRunner(),
                Clock.fixed(NOW, ZoneOffset.UTC), "EGP");
    }

    private void givenWallets(long senderBalance, long recipientBalance) {
        Wallet senderWallet = Wallet.reconstitute(11L, 1L, senderBalance, "EGP", NOW);
        Wallet recipientWallet = Wallet.reconstitute(22L, 2L, recipientBalance, "EGP", NOW);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("sara@example.com")).thenReturn(Optional.of(recipient));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(recipientWallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void successfulTransferMovesMoneyAndPostsBalancedJournal() {
        givenWallets(5_000, 1_000);
        when(transferRepository.findByFromWalletIdAndIdempotencyKey(11L, "key-1")).thenReturn(Optional.empty());

        TransferResult result = useCase.execute(new TransferCommand(1L, "sara@example.com", 2_000, "key-1"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.senderBalanceAfterMinor()).isEqualTo(3_000);

        ArgumentCaptor<LedgerJournal> journalCaptor = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(ledgerEntryRepository).saveAll(journalCaptor.capture());
        LedgerJournal journal = journalCaptor.getValue();
        assertThat(journal.entries()).hasSize(2);
        assertThat(journal.entries().stream().mapToLong(e -> e.amountMinor()).sum()).isZero();

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(transferCaptor.capture());
        assertThat(transferCaptor.getValue().amountMinor()).isEqualTo(2_000);
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutMovingMoneyAgain() {
        givenWallets(5_000, 1_000);
        Transfer stored = Transfer.of("key-1", 11L, 22L, 2_000, "journal-1", NOW);
        when(transferRepository.findByFromWalletIdAndIdempotencyKey(11L, "key-1")).thenReturn(Optional.of(stored));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(Wallet.reconstitute(22L, 2L, 3_000, "EGP", NOW)));

        TransferResult result = useCase.execute(new TransferCommand(1L, "sara@example.com", 2_000, "key-1"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.amountMinor()).isEqualTo(2_000);
        verify(ledgerEntryRepository, never()).saveAll(any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void insufficientFundsIsRejected() {
        givenWallets(1_000, 0);
        when(transferRepository.findByFromWalletIdAndIdempotencyKey(11L, "key-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new TransferCommand(1L, "sara@example.com", 2_000, "key-2")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    @Test
    void transferToYourselfIsRejected() {
        when(transferRepository.findByFromWalletIdAndIdempotencyKey(11L, "key-3")).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> useCase.execute(new TransferCommand(1L, "ahmed@example.com", 100, "key-3")))
                .isInstanceOf(SelfTransferException.class);
    }

    @Test
    void sameIdempotencyKeyWithDifferentAmountIsRejected() {
        givenWallets(5_000, 1_000);
        Transfer stored = Transfer.of("key-1", 11L, 22L, 2_000, "journal-1", NOW);
        when(transferRepository.findByFromWalletIdAndIdempotencyKey(11L, "key-1")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> useCase.execute(new TransferCommand(1L, "sara@example.com", 5_000, "key-1")))
                .isInstanceOf(ConflictException.class);
        verify(ledgerEntryRepository, never()).saveAll(any());
        verify(transferRepository, never()).save(any());
    }

    @Test
    void sameIdempotencyKeyWithDifferentRecipientIsRejected() {
        givenWallets(5_000, 1_000);
        User other = User.reconstitute(3L, "mona@example.com", "Mona M", "hash", UserRole.USER, NOW);
        when(userRepository.findByEmail("mona@example.com")).thenReturn(Optional.of(other));
        when(walletRepository.findByUserId(3L)).thenReturn(Optional.of(Wallet.reconstitute(33L, 3L, 0, "EGP", NOW)));
        when(walletRepository.findByUserIdForUpdate(3L)).thenReturn(Optional.of(Wallet.reconstitute(33L, 3L, 0, "EGP", NOW)));
        Transfer stored = Transfer.of("key-1", 11L, 22L, 2_000, "journal-1", NOW);
        when(transferRepository.findByFromWalletIdAndIdempotencyKey(11L, "key-1")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> useCase.execute(new TransferCommand(1L, "mona@example.com", 2_000, "key-1")))
                .isInstanceOf(ConflictException.class);
        verify(ledgerEntryRepository, never()).saveAll(any());
        verify(transferRepository, never()).save(any());
    }
}
