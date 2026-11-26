package com.wallet.payment.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "topup_reversals", indexes = {
        @Index(name = "idx_topup_reversals_topup", columnList = "topup_id")
})
class TopUpReversalJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topup_id", nullable = false)
    private Long topupId;

    /** Unique: the database-level guarantee that one reversal applies exactly once. */
    @Column(name = "reversal_transaction_id", nullable = false, unique = true)
    private Long reversalTransactionId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TopUpReversalJpaEntity() {
    }

    TopUpReversalJpaEntity(Long id, Long topupId, Long reversalTransactionId,
                           long amountMinor, Instant createdAt) {
        this.id = id;
        this.topupId = topupId;
        this.reversalTransactionId = reversalTransactionId;
        this.amountMinor = amountMinor;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    Long getTopupId() { return topupId; }
    Long getReversalTransactionId() { return reversalTransactionId; }
    long getAmountMinor() { return amountMinor; }
    Instant getCreatedAt() { return createdAt; }
}
