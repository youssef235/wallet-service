package com.wallet.notification.infrastructure;

import com.wallet.notification.application.NotificationService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends plain-text emails over SMTP. When mail is disabled (no SMTP configured,
 * e.g. local dev without Mailhog) it logs instead of sending.
 */
@Service
class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final ExecutorService mailExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mail-sender");
        thread.setDaemon(true);
        return thread;
    });

    EmailNotificationService(JavaMailSender mailSender,
                             @Value("${app.mail.enabled:false}") boolean enabled,
                             @Value("${app.mail.from:wallet-service@localhost}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    @Override
    public void sendWelcome(String toEmail, String fullName) {
        send(toEmail, "Welcome to Wallet Service",
                "Hi " + fullName + ",\n\nYour wallet is ready. You can now top up and transfer money.\n");
    }

    @Override
    public void sendTopUpReceipt(String toEmail, String fullName, long amountMinor, String currency,
                                 long balanceAfterMinor) {
        send(toEmail, "Top-up receipt",
                "Hi " + fullName + ",\n\nYour top-up of " + format(amountMinor) + " " + currency
                        + " was received. New balance: " + format(balanceAfterMinor) + " " + currency + ".\n");
    }

    @Override
    public void sendTransferReceived(String toEmail, String fullName, long amountMinor, String currency,
                                     String senderName, long balanceAfterMinor) {
        send(toEmail, "You received money",
                "Hi " + fullName + ",\n\nYou received " + format(amountMinor) + " " + currency
                        + " from " + senderName + ". New balance: " + format(balanceAfterMinor) + " "
                        + currency + ".\n");
    }

    private void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("Mail disabled - skipped email to {} with subject '{}'", to, subject);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Send only after the money movement commits: an email about a transfer that
            // rolled back would tell the recipient about money that never arrived.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mailExecutor.execute(() -> deliver(to, subject, body));
                }
            });
            return;
        }
        deliver(to, subject, body);
    }

    private void deliver(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException ex) {
            // A notification failure must never roll back a completed money movement.
            log.warn("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        mailExecutor.shutdown();
    }

    private static String format(long amountMinor) {
        return java.math.BigDecimal.valueOf(amountMinor, 2).toPlainString();
    }
}
