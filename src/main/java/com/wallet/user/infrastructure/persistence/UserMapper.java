package com.wallet.user.infrastructure.persistence;

import com.wallet.user.domain.User;

final class UserMapper {

    private UserMapper() {
    }

    static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(user.email(), user.fullName(), user.passwordHash(), user.role(), user.createdAt());
    }

    static User toDomain(UserJpaEntity entity) {
        return User.reconstitute(entity.getId(), entity.getEmail(), entity.getFullName(),
                entity.getPasswordHash(), entity.getRole(), entity.getCreatedAt());
    }
}
