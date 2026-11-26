package com.wallet.recon.infrastructure;

import com.wallet.notification.application.NotificationService;
import com.wallet.payment.application.ExpireStaleTopUpsUseCase;
import com.wallet.recon.application.ReconcileWalletsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly housekeeping: re-verify every wallet against the ledger and expire
 * top-ups that were started but never paid.
 */
@Component
class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final ReconcileWalletsUseCase reconcileWalletsUseCase;
    private final ExpireStaleTopUpsUseCase expireStaleTopUpsUseCase;
    private final NotificationService notificationService;

    MaintenanceScheduler(ReconcileWalletsUseCase reconcileWalletsUseCase,
                         ExpireStaleTopUpsUseCase expireStaleTopUpsUseCase,
                         NotificationService notificationService) {
        this.reconcileWalletsUseCase = reconcileWalletsUseCase;
        this.expireStaleTopUpsUseCase = expireStaleTopUpsUseCase;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${app.maintenance.reconcile-cron:0 0 3 * * *}")
    void reconcile() {
        var report = reconcileWalletsUseCase.execute();
        if (report.clean()) {
            log.info("Reconciliation OK: wallets match the ledger and the ledger is balanced");
        } else {
            log.error("Reconciliation found {} mismatch(es): {}",
                    report.mismatches().size(), report.mismatches());
            // Loud logging is not enough for money drift: page the owner.
            notificationService.sendOpsAlert("Reconciliation mismatch",
                    "Reconciliation found " + report.mismatches().size() + " mismatch(es):\n"
                            + String.join("\n", report.mismatches()));
        }
    }

    @Scheduled(cron = "${app.maintenance.expire-topups-cron:0 */30 * * * *}")
    void expireStaleTopUps() {
        int expired = expireStaleTopUpsUseCase.execute();
        if (expired > 0) {
            log.info("Expired {} stale pending top-up(s)", expired);
        }
    }
}
