package com.wallet.wallet.infrastructure.persistence;

import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class WalletRepositoryAdapter implements WalletRepository {

    private final SpringDataWalletRepository springData;

    WalletRepositoryAdapter(SpringDataWalletRepository springData) {
        this.springData = springData;
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletJpaEntity entity = new WalletJpaEntity(wallet.id(), wallet.userId(), wallet.balanceMinor(),
                wallet.currency(), wallet.createdAt());
        WalletJpaEntity saved = springData.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return springData.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public Optional<Wallet> findByUserIdForUpdate(Long userId) {
        return springData.findByUserIdForUpdate(userId).map(this::toDomain);
    }

    @Override
    public List<Wallet> findAll() {
        return springData.findAll().stream().map(this::toDomain).toList();
    }

    private Wallet toDomain(WalletJpaEntity e) {
        return Wallet.reconstitute(e.getId(), e.getUserId(), e.getBalanceMinor(), e.getCurrency(), e.getCreatedAt());
    }
}
