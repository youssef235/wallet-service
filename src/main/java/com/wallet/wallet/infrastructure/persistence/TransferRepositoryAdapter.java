package com.wallet.wallet.infrastructure.persistence;

import com.wallet.wallet.domain.Transfer;
import com.wallet.wallet.domain.TransferRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class TransferRepositoryAdapter implements TransferRepository {

    private final SpringDataTransferRepository springData;

    TransferRepositoryAdapter(SpringDataTransferRepository springData) {
        this.springData = springData;
    }

    @Override
    public Transfer save(Transfer transfer) {
        TransferJpaEntity saved = springData.save(new TransferJpaEntity(transfer.idempotencyKey(),
                transfer.fromWalletId(), transfer.toWalletId(), transfer.amountMinor(),
                transfer.journalId(), transfer.createdAt()));
        return toDomain(saved);
    }

    @Override
    public Optional<Transfer> findByFromWalletIdAndIdempotencyKey(Long fromWalletId, String idempotencyKey) {
        return springData.findByFromWalletIdAndIdempotencyKey(fromWalletId, idempotencyKey).map(this::toDomain);
    }

    private Transfer toDomain(TransferJpaEntity e) {
        return new Transfer(e.getId(), e.getIdempotencyKey(), e.getFromWalletId(), e.getToWalletId(),
                e.getAmountMinor(), e.getJournalId(), e.getCreatedAt());
    }
}
