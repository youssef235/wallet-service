package com.wallet.wallet.domain;

import java.util.Optional;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
