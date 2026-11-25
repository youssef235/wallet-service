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
    public List<LedgerEntry> findByWalletId(Long walletId, int offset, int limit) {
        String account = LedgerAccount.ofWallet(walletId).value();
        int page = limit > 0 ? offset / limit : 0;
        return springData.findByAccount(account, PageRequest.of(page, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByWalletId(Long walletId) {
        return springData.countByAccount(LedgerAccount.ofWallet(walletId).value());
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
