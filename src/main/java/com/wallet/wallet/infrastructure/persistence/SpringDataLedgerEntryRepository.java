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

    @Query("SELECT w.id, w.balanceMinor, COALESCE(SUM(e.amountMinor), 0)"
            + " FROM WalletJpaEntity w LEFT JOIN LedgerEntryJpaEntity e"
            + " ON e.account = CONCAT('WALLET:', CAST(w.id AS String))"
            + " GROUP BY w.id, w.balanceMinor"
            + " HAVING w.balanceMinor <> COALESCE(SUM(e.amountMinor), 0)")
    List<Object[]> findWalletBalanceDrift();

    @Query("SELECT COALESCE(SUM(e.amountMinor), 0) FROM LedgerEntryJpaEntity e")
    long sumAllEntries();

    @Query("SELECT e.journalId FROM LedgerEntryJpaEntity e"
            + " GROUP BY e.journalId HAVING SUM(e.amountMinor) <> 0")
    List<String> findUnbalancedJournalIds();
}
