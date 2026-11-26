package com.wallet.payment.infrastructure.persistence;

import com.wallet.payment.domain.TopUpReversal;
import com.wallet.payment.domain.TopUpReversalRepository;
import org.springframework.stereotype.Component;

@Component
class TopUpReversalRepositoryAdapter implements TopUpReversalRepository {

    private final SpringDataTopUpReversalRepository springData;

    TopUpReversalRepositoryAdapter(SpringDataTopUpReversalRepository springData) {
        this.springData = springData;
    }

    @Override
    public TopUpReversal save(TopUpReversal reversal) {
        TopUpReversalJpaEntity saved = springData.save(new TopUpReversalJpaEntity(reversal.id(),
                reversal.topupId(), reversal.reversalTransactionId(), reversal.amountMinor(),
                reversal.createdAt()));
        return new TopUpReversal(saved.getId(), saved.getTopupId(), saved.getReversalTransactionId(),
                saved.getAmountMinor(), saved.getCreatedAt());
    }

    @Override
    public long sumAmountByTopupId(Long topupId) {
        return springData.sumAmountByTopupId(topupId);
    }
}
