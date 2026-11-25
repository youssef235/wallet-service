package com.wallet.payment.application;

public record StartTopUpCommand(Long userId, long amountMinor) {
}
