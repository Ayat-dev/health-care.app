package com.clinic.backend.notification;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.billing.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Time-based notification jobs. Mirrors the pharmacy {@code StockAlertService} cron shape
 * ({@code @EnableScheduling} is already active on the application). The per-minute drain ships
 * whatever upstream modules enqueued; the daily jobs generate scheduled reminders/dunning.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    /** Day offset after which an unpaid invoice is dunned. */
    private static final int INVOICE_OVERDUE_DAYS = 7;

    private final NotificationService notificationService;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;

    /** Every minute — dispatch the pending queue. */
    @Scheduled(cron = "0 * * * * *")
    public void drainQueue() {
        notificationService.processQueue();
    }

    /** Every day at 18:00 — SMS reminders for tomorrow's appointments (once per appointment). */
    @Scheduled(cron = "0 0 18 * * *")
    @Transactional
    public void sendAppointmentReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> due = appointmentRepository.findPendingReminders(
                tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay());
        for (Appointment a : due) {
            notificationService.notifyAppointmentReminder(a);
            a.setReminderSent(true);
            appointmentRepository.save(a);
        }
        if (!due.isEmpty()) {
            log.info("[Notif] Rappels RDV J-1 : {} programmé(s) pour le {}.", due.size(), tomorrow);
        }
    }

    /** Every day at 08:30 — SMS dunning for invoices unpaid more than {@value #INVOICE_OVERDUE_DAYS} days. */
    @Scheduled(cron = "0 30 8 * * *")
    @Transactional
    public void sendOverdueInvoiceReminders() {
        LocalDateTime cutoff = LocalDate.now().minusDays(INVOICE_OVERDUE_DAYS).atStartOfDay();
        List<Invoice> overdue = invoiceRepository.findOverdueUnpaid(cutoff);
        for (Invoice inv : overdue) {
            notificationService.notifyInvoiceOverdue(inv);
        }
        if (!overdue.isEmpty()) {
            log.info("[Notif] Relances facture : {} facture(s) impayée(s) > {} j.",
                    overdue.size(), INVOICE_OVERDUE_DAYS);
        }
    }
}
