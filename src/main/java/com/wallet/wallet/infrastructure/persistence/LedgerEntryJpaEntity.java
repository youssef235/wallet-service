package com.wallet.wallet.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_journal", columnList = "journal_id"),
        @Index(name = "idx_ledger_account", columnList = "account")
})
class LedgerEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_id", nullable = false, length = 36)
    private String journalId;

    @Column(nullable = false, length = 40)
    private String account;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntryJpaEntity() {
    }

    LedgerEntryJpaEntity(String journalId, String account, long amountMinor, String description, Instant createdAt) {
        this.journalId = journalId;
        this.account = account;
        this.amountMinor = amountMinor;
        this.description = description;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    String getJournalId() { return journalId; }
    String getAccount() { return account; }
    long getAmountMinor() { return amountMinor; }
    String getDescription() { return description; }
    Instant getCreatedAt() { return createdAt; }
}
