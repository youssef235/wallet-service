package com.wallet.wallet.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "transfers")
class TransferJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 80)
    private String idempotencyKey;

    @Column(name = "from_wallet_id", nullable = false)
    private Long fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private Long toWalletId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "journal_id", nullable = false, length = 36)
    private String journalId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransferJpaEntity() {
    }

    TransferJpaEntity(String idempotencyKey, Long fromWalletId, Long toWalletId, long amountMinor,
                      String journalId, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amountMinor = amountMinor;
        this.journalId = journalId;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    String getIdempotencyKey() { return idempotencyKey; }
    Long getFromWalletId() { return fromWalletId; }
    Long getToWalletId() { return toWalletId; }
    long getAmountMinor() { return amountMinor; }
    String getJournalId() { return journalId; }
    Instant getCreatedAt() { return createdAt; }
}
