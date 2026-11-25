package com.wallet.user.application;

import com.wallet.user.domain.UserRole;

public interface TokenService {

    IssuedToken generate(Long userId, String email, UserRole role);
}
