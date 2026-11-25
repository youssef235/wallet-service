package com.wallet.user.presentation;

import jakarta.validation.constraints.NotBlank;

record LoginUserRequest(@NotBlank String email, @NotBlank String password) {
}
