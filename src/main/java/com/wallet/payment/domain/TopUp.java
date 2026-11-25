package com.wallet.payment.domain;

import java.time.Instant;

/**
 * A wallet top-up initiated through Paymob. Money is credited only after the
 * HMAC-verified webhook confirms success, and exactly once per Paymob
 * transaction id (unique constraint + row lock).
 */
public class TopUp {

    private final Long id;
    private final Long userId;
    private final long amountMinor;
    private final String currency;
    private final TopUpStatus status;
    private final String merchantOrderId;
    private final Long paymobOrderId;
    private final Long paymobTransactionId;
    private final String journalId;
    private final Instant createdAt;

    private TopUp(Long id, Long userId, long amountMinor, String currency, TopUpStatus status,
                  String merchantOrderId, Long paymobOrderId, Long paymobTransactionId,
                  String journalId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.merchantOrderId = merchantOrderId;
        this.paymobOrderId = paymobOrderId;
        this.paymobTransactionId = paymobTransactionId;
        this.journalId = journalId;
        this.createdAt = createdAt;
    }

    public static TopUp pending(Long userId, long amountMinor, String currency,
                                String merchantOrderId, Instant now) {
        if (amountMinor <= 0) {
            throw new InvalidTopUpException("Top-up amount must be positive");
        }
        return new TopUp(null, userId, amountMinor, currency, TopUpStatus.PENDING,
                merchantOrderId, null, null, null, now);
    }

    public static TopUp reconstitute(Long id, Long userId, long amountMinor, String currency,
                                     TopUpStatus status, String merchantOrderId, Long paymobOrderId,
                                     Long paymobTransactionId, String journalId, Instant createdAt) {
        return new TopUp(id, userId, amountMinor, currency, status, merchantOrderId, paymobOrderId,
                paymobTransactionId, journalId, createdAt);
    }

    public TopUp withPaymobOrder(Long paymobOrderId) {
        return new TopUp(id, userId, amountMinor, currency, status, merchantOrderId,
                paymobOrderId, paymobTransactionId, journalId, createdAt);
    }

    public TopUp markSucceeded(Long paymobTransactionId, String journalId) {
        if (status != TopUpStatus.PENDING) {
            throw new InvalidTopUpException("Only a pending top-up can succeed, current: " + status);
        }
        return new TopUp(id, userId, amountMinor, currency, TopUpStatus.SUCCESS,
                merchantOrderId, paymobOrderId, paymobTransactionId, journalId, createdAt);
    }

    public TopUp markFailed() {
        if (status != TopUpStatus.PENDING) {
            throw new InvalidTopUpException("Only a pending top-up can fail, current: " + status);
        }
        return new TopUp(id, userId, amountMinor, currency, TopUpStatus.FAILED,
                merchantOrderId, paymobOrderId, paymobTransactionId, journalId, createdAt);
    }

    public TopUp markExpired() {
        if (status != TopUpStatus.PENDING) {
            throw new InvalidTopUpException("Only a pending top-up can expire, current: " + status);
        }
        return new TopUp(id, userId, amountMinor, currency, TopUpStatus.EXPIRED,
                merchantOrderId, paymobOrderId, paymobTransactionId, journalId, createdAt);
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public long amountMinor() { return amountMinor; }
    public String currency() { return currency; }
    public TopUpStatus status() { return status; }
    public String merchantOrderId() { return merchantOrderId; }
    public Long paymobOrderId() { return paymobOrderId; }
    public Long paymobTransactionId() { return paymobTransactionId; }
    public String journalId() { return journalId; }
    public Instant createdAt() { return createdAt; }
}
