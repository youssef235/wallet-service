package com.wallet.wallet.domain;

import java.time.Instant;

/**
 * A user's wallet. The balance is a cached projection of the ledger:
 * it is only ever mutated inside the same database transaction that appends
 * the matching journal entries, and the reconciliation job re-derives it.
 *
 * All amounts are minor units (piasters for EGP) to avoid floating point money bugs.
 */
public class Wallet {

    private final Long id;
    private final Long userId;
    private final long balanceMinor;
    private final String currency;
    private final Instant createdAt;

    private Wallet(Long id, Long userId, long balanceMinor, String currency, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.balanceMinor = balanceMinor;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public static Wallet openFor(Long userId, String currency, Instant now) {
        return new Wallet(null, userId, 0, currency, now);
    }

    public static Wallet reconstitute(Long id, Long userId, long balanceMinor, String currency, Instant createdAt) {
        return new Wallet(id, userId, balanceMinor, currency, createdAt);
    }

    public Wallet credit(long amountMinor) {
        requirePositive(amountMinor);
        return new Wallet(id, userId, Math.addExact(balanceMinor, amountMinor), currency, createdAt);
    }

    /**
     * Claws money back after a refund/chargeback. Unlike debit(), this is allowed
     * to take the balance negative: the payment provider took the money back, so
     * the user owes it until they top up again. Callers must log an alert when
     * the result goes negative.
     */
    public Wallet applyReversal(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Reversal amount must be positive");
        }
        return new Wallet(id, userId, balanceMinor - amountMinor, currency, createdAt);
    }

    public Wallet debit(long amountMinor) {
        requirePositive(amountMinor);
        if (balanceMinor < amountMinor) {
            throw new InsufficientFundsException(balanceMinor, amountMinor);
        }
        return new Wallet(id, userId, balanceMinor - amountMinor, currency, createdAt);
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new InvalidAmountException("Amount must be positive");
        }
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public long balanceMinor() { return balanceMinor; }
    public String currency() { return currency; }
    public Instant createdAt() { return createdAt; }
}
