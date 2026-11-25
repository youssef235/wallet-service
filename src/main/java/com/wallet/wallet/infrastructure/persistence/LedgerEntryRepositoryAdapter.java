package com.wallet.wallet.infrastructure.persistence;

import com.wallet.wallet.domain.LedgerAccount;
import com.wallet.wallet.domain.LedgerEntry;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.LedgerJournal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class LedgerEntryRepositoryAdapter implements LedgerEntryRepository {

    private final SpringDataLedgerEntryRepository springData;

    LedgerEntryRepositoryAdapter(SpringDataLedgerEntryRepository springData) {
        this.springData = springData;
    }

    @Override
    public void saveAll(LedgerJournal journal) {
        var entities = journal.entries().stream()
                .map(e -> new LedgerEntryJpaEntity(e.journalId(), e.account().value(), e.amountMinor(),
                        e.description(), e.createdAt()))
                .toList();
        springData.saveAll(entities);
    }

    @Override
    public List<LedgerEntry> findByWalletId(Long walletId, Long beforeId, int limit) {
        String account = LedgerAccount.ofWallet(walletId).value();
        return springData.findPage(account, beforeId, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<WalletBalanceDrift> findWalletBalanceDrift() {
        return springData.findWalletBalanceDrift().stream()
                .map(row -> new WalletBalanceDrift((Long) row[0], (Long) row[1], (Long) row[2]))
                .toList();
    }

    @Override
    public long sumAllEntries() {
        return springData.sumAllEntries();
    }

    @Override
    public List<String> findUnbalancedJournalIds() {
        return springData.findUnbalancedJournalIds();
    }

    @Override
    public long sumForWallet(Long walletId) {
        return springData.sumByAccount(LedgerAccount.ofWallet(walletId).value());
    }

    private LedgerEntry toDomain(LedgerEntryJpaEntity e) {
        return new LedgerEntry(e.getId(), e.getJournalId(), new LedgerAccount(e.getAccount()),
                e.getAmountMinor(), e.getDescription(), e.getCreatedAt());
    }
}
