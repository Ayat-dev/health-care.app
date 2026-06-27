package com.clinic.backend.notification;

import com.clinic.backend.tenant.Clinic;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Time-based notification jobs. Mirrors the pharmacy {@code StockAlertService} cron shape
 * ({@code @EnableScheduling} is already active on the application). The per-minute drain ships
 * whatever upstream modules enqueued; the daily jobs generate scheduled reminders/dunning.
 * <p>
 * Multi-tenant (P4.2) : ces tâches tournent <b>sans</b> contexte de requête → tenant sentinelle
 * « fermé » (lectures vides : RDV/factures/notifs sont {@code @TenantId}). Chaque job itère donc
 * les cliniques actives via {@link TenantContext#runAs} et délègue le travail transactionnel aux
 * méthodes (proxytisées) de {@link NotificationService} — la session s'ouvre ainsi <b>après</b> que
 * le tenant est posé (piège de timing P4.2 : Hibernate résout le tenant à l'ouverture de session).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    /** Day offset after which an unpaid invoice is dunned. */
    private static final int INVOICE_OVERDUE_DAYS = 7;

    private final NotificationService notificationService;
    private final ClinicRepository clinicRepository;

    /** Every minute — dispatch the pending queue (per active clinic). */
    @Scheduled(cron = "0 * * * * *")
    public void drainQueue() {
        forEachActiveClinic(notificationService::processQueue);
    }

    /** Every day at 18:00 — SMS reminders for tomorrow's appointments (once per appointment). */
    @Scheduled(cron = "0 0 18 * * *")
    public void sendAppointmentReminders() {
        forEachActiveClinic(clinic -> {
            int n = notificationService.runAppointmentReminders();
            if (n > 0) log.info("[Notif][{}] Rappels RDV J-1 : {} programmé(s).", clinic.getCode(), n);
        });
    }

    /** Every day at 08:30 — SMS dunning for invoices unpaid more than {@value #INVOICE_OVERDUE_DAYS} days. */
    @Scheduled(cron = "0 30 8 * * *")
    public void sendOverdueInvoiceReminders() {
        forEachActiveClinic(clinic -> {
            int n = notificationService.runInvoiceDunning(INVOICE_OVERDUE_DAYS);
            if (n > 0) log.info("[Notif][{}] Relances facture : {} facture(s) impayée(s) > {} j.",
                    clinic.getCode(), n, INVOICE_OVERDUE_DAYS);
        });
    }

    /** Exécute {@code body} une fois par clinique active, dans son contexte tenant. */
    private void forEachActiveClinic(java.util.function.Consumer<Clinic> body) {
        for (Clinic clinic : clinicRepository.findByActiveTrue()) {
            TenantContext.runAs(clinic.getId(), () -> body.accept(clinic));
        }
    }

    /** Variante sans la clinique pour les corps qui n'en ont pas besoin (drain). */
    private void forEachActiveClinic(Runnable body) {
        for (Clinic clinic : clinicRepository.findByActiveTrue()) {
            TenantContext.runAs(clinic.getId(), body);
        }
    }
}
