package com.wallet.user.infrastructure.persistence;

import com.wallet.user.domain.User;
import com.wallet.user.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springData;

    UserRepositoryAdapter(SpringDataUserRepository springData) {
        this.springData = springData;
    }

    @Override
    public User save(User user) {
        return UserMapper.toDomain(springData.save(UserMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findById(Long id) {
        return springData.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springData.findByEmail(email).map(UserMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springData.existsByEmail(email);
    }
}
