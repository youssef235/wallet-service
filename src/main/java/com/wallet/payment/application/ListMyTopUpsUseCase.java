package com.wallet.payment.application;

import com.wallet.payment.domain.TopUpRepository;
import com.wallet.shared.application.TransactionRunner;
import java.util.List;

public class ListMyTopUpsUseCase {

    private final TopUpRepository topUpRepository;
    private final TransactionRunner transactionRunner;

    public ListMyTopUpsUseCase(TopUpRepository topUpRepository, TransactionRunner transactionRunner) {
        this.topUpRepository = topUpRepository;
        this.transactionRunner = transactionRunner;
    }

    public List<TopUpResult> execute(Long userId) {
        return transactionRunner.inTransaction(() -> topUpRepository.findByUserId(userId).stream()
                .map(TopUpResult::from)
                .toList());
    }
}
