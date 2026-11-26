package com.wallet.notification.application;

/**
 * Outgoing user notifications. Implementations must never fail the caller's
 * business operation: a lost email must not roll back a money movement.
 */
public interface NotificationService {

    void sendWelcome(String toEmail, String fullName);

    void sendTopUpReceipt(String toEmail, String fullName, long amountMinor, String currency,
                          long balanceAfterMinor);

    void sendTransferReceived(String toEmail, String fullName, long amountMinor, String currency,
                              String senderName, long balanceAfterMinor);

    /** Operational alert to the service owner (e.g. reconciliation drift). */
    void sendOpsAlert(String subject, String text);
}
