package com.wallet.payment.infrastructure.persistence;

import com.wallet.payment.domain.TopUp;
import com.wallet.payment.domain.TopUpRepository;
import com.wallet.payment.domain.TopUpStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class TopUpRepositoryAdapter implements TopUpRepository {

    private final SpringDataTopUpRepository springData;

    TopUpRepositoryAdapter(SpringDataTopUpRepository springData) {
        this.springData = springData;
    }

    @Override
    public TopUp save(TopUp topUp) {
        TopUpJpaEntity saved = springData.save(new TopUpJpaEntity(topUp.id(), topUp.userId(),
                topUp.amountMinor(), topUp.currency(), topUp.status(), topUp.merchantOrderId(),
                topUp.paymobOrderId(), topUp.paymobTransactionId(), topUp.journalId(),
                topUp.createdAt()));
        return toDomain(saved);
    }

    @Override
    public Optional<TopUp> findByMerchantOrderId(String merchantOrderId) {
        return springData.findByMerchantOrderId(merchantOrderId).map(this::toDomain);
    }

    @Override
    public Optional<TopUp> findByMerchantOrderIdForUpdate(String merchantOrderId) {
        return springData.findByMerchantOrderIdForUpdate(merchantOrderId).map(this::toDomain);
    }

    @Override
    public boolean existsByPaymobTransactionId(Long paymobTransactionId) {
        return springData.existsByPaymobTransactionId(paymobTransactionId);
    }

    @Override
    public Optional<TopUp> findByPaymobTransactionIdForUpdate(Long paymobTransactionId) {
        return springData.findByPaymobTransactionIdForUpdate(paymobTransactionId).map(this::toDomain);
    }

    @Override
    public Optional<TopUp> findByPaymobOrderIdForUpdate(Long paymobOrderId) {
        return springData.findByPaymobOrderIdForUpdate(paymobOrderId).map(this::toDomain);
    }

    @Override
    public List<TopUp> findByUserId(Long userId) {
        return springData.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<TopUp> findPendingOlderThan(Instant cutoff) {
        return springData.findByStatusAndCreatedAtBefore(TopUpStatus.PENDING, cutoff).stream()
                .map(this::toDomain).toList();
    }

    private TopUp toDomain(TopUpJpaEntity e) {
        return TopUp.reconstitute(e.getId(), e.getUserId(), e.getAmountMinor(), e.getCurrency(),
                e.getStatus(), e.getMerchantOrderId(), e.getPaymobOrderId(), e.getPaymobTransactionId(),
                e.getJournalId(), e.getCreatedAt());
    }
}
