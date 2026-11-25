package com.wallet.wallet.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wallets")
class WalletJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletJpaEntity() {
    }

    WalletJpaEntity(Long id, Long userId, long balanceMinor, String currency, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.balanceMinor = balanceMinor;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    Long getUserId() { return userId; }
    long getBalanceMinor() { return balanceMinor; }
    String getCurrency() { return currency; }
    Instant getCreatedAt() { return createdAt; }
}
