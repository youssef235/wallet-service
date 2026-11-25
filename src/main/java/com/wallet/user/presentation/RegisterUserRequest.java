package com.wallet.user.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
