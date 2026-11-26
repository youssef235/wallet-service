package com.wallet.payment.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTopUpReversalRepository extends JpaRepository<TopUpReversalJpaEntity, Long> {

    @Query("SELECT COALESCE(SUM(r.amountMinor), 0) FROM TopUpReversalJpaEntity r WHERE r.topupId = :topupId")
    long sumAmountByTopupId(@Param("topupId") Long topupId);
}
