package com.wallet.wallet.domain;

import java.util.Optional;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findByUserId(Long userId);

    /** SELECT ... FOR UPDATE: blocks concurrent writers until this transaction ends. */
    Optional<Wallet> findByUserIdForUpdate(Long userId);

    java.util.List<Wallet> findAll();
}
