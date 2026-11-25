package com.wallet.wallet.application;

public record TransferCommand(Long senderUserId, String recipientEmail, long amountMinor, String idempotencyKey) {
}
