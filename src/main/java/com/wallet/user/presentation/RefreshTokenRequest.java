package com.wallet.user.presentation;

import jakarta.validation.constraints.NotBlank;

record RefreshTokenRequest(@NotBlank String refreshToken) {
}
