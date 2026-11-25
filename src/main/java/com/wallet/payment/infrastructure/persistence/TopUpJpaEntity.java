package com.wallet.payment.infrastructure.persistence;

import com.wallet.payment.domain.TopUpStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "topups", indexes = {
        @Index(name = "idx_topups_user", columnList = "user_id"),
        @Index(name = "idx_topups_status_created", columnList = "status,created_at")
})
class TopUpJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopUpStatus status;

    @Column(name = "merchant_order_id", nullable = false, unique = true, length = 80)
    private String merchantOrderId;

    @Column(name = "paymob_order_id")
    private Long paymobOrderId;

    /** Unique: the database-level guarantee that one Paymob payment credits the wallet once. */
    @Column(name = "paymob_transaction_id", unique = true)
    private Long paymobTransactionId;

    @Column(name = "journal_id", length = 36)
    private String journalId;

    @Column(name = "reversal_transaction_id", unique = true)
    private Long reversalTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TopUpJpaEntity() {
    }

    TopUpJpaEntity(Long id, Long userId, long amountMinor, String currency, TopUpStatus status,
                   String merchantOrderId, Long paymobOrderId, Long paymobTransactionId,
                   String journalId, Long reversalTransactionId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.merchantOrderId = merchantOrderId;
        this.paymobOrderId = paymobOrderId;
        this.paymobTransactionId = paymobTransactionId;
        this.journalId = journalId;
        this.reversalTransactionId = reversalTransactionId;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    Long getUserId() { return userId; }
    long getAmountMinor() { return amountMinor; }
    String getCurrency() { return currency; }
    TopUpStatus getStatus() { return status; }
    String getMerchantOrderId() { return merchantOrderId; }
    Long getPaymobOrderId() { return paymobOrderId; }
    Long getPaymobTransactionId() { return paymobTransactionId; }
    String getJournalId() { return journalId; }
    Long getReversalTransactionId() { return reversalTransactionId; }
    Instant getCreatedAt() { return createdAt; }
}
