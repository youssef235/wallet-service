package com.wallet.user.infrastructure.persistence;

import com.wallet.user.domain.RefreshToken;
import com.wallet.user.domain.RefreshTokenRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository springData;

    RefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository springData) {
        this.springData = springData;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(token.id(), token.userId(), token.tokenHash(),
                token.expiresAt(), token.revoked(), token.replacedByHash(), token.createdAt());
        RefreshTokenJpaEntity saved = springData.save(entity);
        return RefreshToken.reconstitute(saved.getId(), saved.getUserId(), saved.getTokenHash(),
                saved.getExpiresAt(), saved.isRevoked(), saved.getReplacedByHash(), saved.getCreatedAt());
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return springData.findByTokenHash(tokenHash)
                .map(e -> RefreshToken.reconstitute(e.getId(), e.getUserId(), e.getTokenHash(),
                        e.getExpiresAt(), e.isRevoked(), e.getReplacedByHash(), e.getCreatedAt()));
    }

    @Override
    public void revokeAllForUser(Long userId) {
        springData.revokeAllForUser(userId);
    }
}
