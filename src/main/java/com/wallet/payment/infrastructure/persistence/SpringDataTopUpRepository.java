package com.wallet.payment.infrastructure.persistence;

import com.wallet.payment.domain.TopUpStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTopUpRepository extends JpaRepository<TopUpJpaEntity, Long> {

    Optional<TopUpJpaEntity> findByMerchantOrderId(String merchantOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TopUpJpaEntity t WHERE t.merchantOrderId = :merchantOrderId")
    Optional<TopUpJpaEntity> findByMerchantOrderIdForUpdate(@Param("merchantOrderId") String merchantOrderId);

    boolean existsByPaymobTransactionId(Long paymobTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TopUpJpaEntity t WHERE t.paymobTransactionId = :paymobTransactionId")
    Optional<TopUpJpaEntity> findByPaymobTransactionIdForUpdate(@Param("paymobTransactionId") Long paymobTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TopUpJpaEntity t WHERE t.paymobOrderId = :paymobOrderId")
    Optional<TopUpJpaEntity> findByPaymobOrderIdForUpdate(@Param("paymobOrderId") Long paymobOrderId);

    List<TopUpJpaEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<TopUpJpaEntity> findByStatusAndCreatedAtBefore(TopUpStatus status, Instant cutoff);
}
