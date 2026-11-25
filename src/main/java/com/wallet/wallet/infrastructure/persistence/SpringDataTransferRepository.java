package com.wallet.wallet.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTransferRepository extends JpaRepository<TransferJpaEntity, Long> {

    Optional<TransferJpaEntity> findByFromWalletIdAndIdempotencyKey(Long fromWalletId, String idempotencyKey);
}
