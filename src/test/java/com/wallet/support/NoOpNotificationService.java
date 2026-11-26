package com.wallet.support;

import com.wallet.notification.application.NotificationService;

public class NoOpNotificationService implements NotificationService {

    @Override
    public void sendWelcome(String toEmail, String fullName) {
    }

    @Override
    public void sendTopUpReceipt(String toEmail, String fullName, long amountMinor, String currency,
                                 long balanceAfterMinor) {
    }

    @Override
    public void sendTransferReceived(String toEmail, String fullName, long amountMinor, String currency,
                                     String senderName, long balanceAfterMinor) {
    }

    @Override
    public void sendOpsAlert(String subject, String text) {
    }
}
