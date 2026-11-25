package com.wallet.user.application;

import com.wallet.user.domain.User;

public record UserResult(Long id, String email, String fullName, String role) {

    public static UserResult from(User user) {
        return new UserResult(user.id(), user.email(), user.fullName(), user.role().name());
    }
}
