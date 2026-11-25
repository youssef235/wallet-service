package com.wallet.user.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.user.domain.RefreshTokenRepository;
import java.util.Optional;

public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final TransactionRunner transactionRunner;

    public LogoutUseCase(RefreshTokenRepository refreshTokenRepository,
                         TokenHasher tokenHasher,
                         TransactionRunner transactionRunner) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.transactionRunner = transactionRunner;
    }

    public void execute(String presentedToken) {
        transactionRunner.inTransaction(() ->
                Optional.ofNullable(presentedToken)
                        .flatMap(token -> refreshTokenRepository.findByTokenHash(tokenHasher.hash(token)))
                        .ifPresent(token -> refreshTokenRepository.save(token.revoke())));
    }
}
