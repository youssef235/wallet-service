package com.wallet.wallet.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record TransferRequest(
        @NotBlank @Email String recipientEmail,
        @NotNull @Positive @Max(10_000_000) Long amountMinor,
        @NotBlank @Size(min = 8, max = 80) String idempotencyKey) {
}
