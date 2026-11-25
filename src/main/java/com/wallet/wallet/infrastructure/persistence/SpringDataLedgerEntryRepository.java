package com.wallet.wallet.infrastructure.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryJpaEntity, Long> {

    @Query("SELECT e FROM LedgerEntryJpaEntity e WHERE e.account = :account"
            + " AND (:beforeId IS NULL OR e.id < :beforeId) ORDER BY e.id DESC")
    List<LedgerEntryJpaEntity> findPage(@Param("account") String account,
                                        @Param("beforeId") Long beforeId,
                                        Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.amountMinor), 0) FROM LedgerEntryJpaEntity e WHERE e.account = :account")
    long sumByAccount(@Param("account") String account);
}
