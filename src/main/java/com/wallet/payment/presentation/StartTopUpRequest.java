package com.wallet.payment.presentation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record StartTopUpRequest(@NotNull @Positive @Max(10_000_000) Long amountMinor) {
}
