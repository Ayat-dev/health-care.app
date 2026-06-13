package com.clinic.backend.notification;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.NotificationDto;
import com.clinic.backend.lab.LabRequest;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Central queue façade. Upstream modules call the {@code notify*} helpers to enqueue messages
 * (status EN_ATTENTE); {@link NotificationScheduler} periodically calls {@link #processQueue()}
 * to drain them through a {@link NotificationSender}. Enqueue failures must never break the
 * caller's own transaction — the helpers swallow and log, mirroring the pharmacy stock-alert job.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService {

    /** How many queued rows a single drain pass dispatches. */
    private static final int DRAIN_BATCH = 100;

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FR = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ClinicConfigService clinicConfigService;
    private final List<NotificationSender> senders;

    // ── Enqueue (bas niveau) ─────────────────────────────────────────────────────
    /**
     * Build and persist a queued notification. {@code recipient} falls back to a sensible
     * default (patient phone / user name) when blank. Returns the saved row, or null if it
     * could not even be queued (never throws to the caller).
     */
    public Notification enqueue(String type, String channel, User user, Patient patient,
                                String recipient, String subject, String body,
                                LocalDateTime scheduledAt) {
        try {
            if (body == null || body.isBlank()) return null;
            String to = recipient != null && !recipient.isBlank()
                    ? recipient.trim()
                    : defaultRecipient(channel, user, patient);
            if (to == null || to.isBlank()) {
                log.warn("[Notif] {} {} ignorée : aucun destinataire", type, channel);
                return null;
            }
            Notification n = new Notification();
            n.setType(type);
            n.setChannel(channel);
            n.setUser(user);
            n.setPatient(patient);
            n.setRecipient(to);
            n.setSubject(subject);
            n.setBody(body.trim());
            n.setScheduledAt(scheduledAt);
            n.setStatus("EN_ATTENTE");
            return notificationRepository.save(n);
        } catch (RuntimeException e) {
            log.error("[Notif] Échec de mise en file ({} {}): {}", type, channel, e.getMessage());
            return null;
        }
    }

    /** Enqueue one IN_APP notification to every active user holding {@code role}. */
    public void enqueueInAppToRole(String type, String role, Patient patient, String subject, String body) {
        for (User u : userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc(role)) {
            enqueue(type, "IN_APP", u, patient, u.getUsername(), subject, body, null);
        }
    }

    // ── Déclencheurs métier (appelés par les modules amont) ──────────────────────
    /** RDV créé → SMS immédiat au patient. */
    public void notifyAppointmentCreated(Appointment a) {
        if (a == null || a.getPatient() == null) return;
        Patient p = a.getPatient();
        String doctor = a.getDoctor() != null ? a.getDoctor().getFullName() : "votre médecin";
        String when = a.getStartTime() != null
                ? a.getStartTime().format(DATE_FR) + " à " + a.getStartTime().format(TIME_FR)
                : "la date prévue";
        String body = "[" + clinicName() + "] RDV enregistré le " + when + " avec " + doctor
                + ". Info: " + clinicPhone();
        enqueue("RAPPEL_RDV", "SMS", null, p, p.getPhone(), "Rendez-vous confirmé", body, null);
    }

    /** Rappel J-1 (programmé par le scheduler) → SMS au patient. */
    public void notifyAppointmentReminder(Appointment a) {
        if (a == null || a.getPatient() == null || a.getStartTime() == null) return;
        Patient p = a.getPatient();
        String doctor = a.getDoctor() != null ? a.getDoctor().getFullName() : "votre médecin";
        String body = "[" + clinicName() + "] Rappel: RDV demain " + a.getStartTime().format(DATE_FR)
                + " à " + a.getStartTime().format(TIME_FR) + " avec " + doctor
                + ". Info: " + clinicPhone();
        enqueue("RAPPEL_RDV", "SMS", null, p, p.getPhone(), "Rappel de rendez-vous", body, null);
    }

    /** Résultats labo validés → SMS au patient + IN_APP au médecin prescripteur. */
    public void notifyLabResultsValidated(LabRequest r) {
        if (r == null || r.getPatient() == null) return;
        Patient p = r.getPatient();
        String body = "[" + clinicName() + "] Vos résultats d'analyse (" + r.getRequestNumber()
                + ") sont disponibles. Contactez-nous: " + clinicPhone();
        enqueue("RESULTAT_LABO", "SMS", null, p, p.getPhone(), "Résultats disponibles", body, null);

        if (r.getDoctor() != null) {
            String inApp = "Résultats validés pour " + p.getFullName()
                    + " — demande " + r.getRequestNumber() + ".";
            enqueue("RESULTAT_LABO", "IN_APP", r.getDoctor(), p, r.getDoctor().getUsername(),
                    "Résultats de laboratoire", inApp, null);
        }
    }

    /** Facture impayée (J+7, programmé par le scheduler) → SMS au patient. */
    public void notifyInvoiceOverdue(Invoice inv) {
        if (inv == null || inv.getPatient() == null) return;
        Patient p = inv.getPatient();
        String body = "[" + clinicName() + "] Facture " + inv.getInvoiceNumber() + " en attente de règlement ("
                + inv.getBalanceDue().toPlainString() + " " + currency() + "). Info: " + clinicPhone();
        enqueue("FACTURE_IMPAYEE", "SMS", null, p, p.getPhone(), "Facture en attente", body, null);
    }

    /** Alerte stock pharmacie → IN_APP à tous les pharmaciens. */
    public void notifyStockAlert(String subject, String body) {
        enqueueInAppToRole("STOCK_ALERTE", "PHARMACIEN", null, subject, body);
    }

    // ── Drainage de la file (appelé par le scheduler) ────────────────────────────
    /** Dispatch every due EN_ATTENTE row through its channel sender. Returns the count sent. */
    public int processQueue() {
        List<Notification> due = notificationRepository.findDue(LocalDateTime.now());
        if (due.isEmpty()) return 0;
        int sent = 0;
        for (Notification n : due.stream().limit(DRAIN_BATCH).toList()) {
            if (dispatch(n)) sent++;
        }
        if (sent > 0) log.info("[Notif] File drainée : {} message(s) traité(s).", sent);
        return sent;
    }

    private boolean dispatch(Notification n) {
        NotificationSender sender = senders.stream()
                .filter(s -> s.supports(n.getChannel()))
                .findFirst().orElse(null);
        try {
            if (sender == null) {
                throw new IllegalStateException("Aucun émetteur pour le canal " + n.getChannel());
            }
            sender.send(n);
            n.setStatus("ENVOYE");
            n.setSentAt(LocalDateTime.now());
            n.setErrorMessage(null);
            notificationRepository.save(n);
            return true;
        } catch (Exception e) {
            n.setStatus("ECHEC");
            n.setErrorMessage(e.getMessage());
            notificationRepository.save(n);
            log.warn("[Notif] Échec d'envoi #{} ({}): {}", n.getId(), n.getChannel(), e.getMessage());
            return false;
        }
    }

    // ── In-app (lecture, badge, marquage) ────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<NotificationDto> inboxForCurrentUser() {
        User user = currentUser();
        if (user == null) return List.of();
        return notificationRepository.findInboxForUser(user.getId()).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCountForCurrentUser() {
        User user = currentUser();
        return user != null ? notificationRepository.countUnreadForUser(user.getId()) : 0;
    }

    /** Mark one of the current user's in-app notifications read (no-op if not theirs). */
    public void markRead(Long id) {
        User user = currentUser();
        if (user == null) return;
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser() != null && n.getUser().getId().equals(user.getId()) && n.getReadAt() == null) {
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        });
    }

    /** Mark all of the current user's in-app notifications read. */
    public void markAllRead() {
        User user = currentUser();
        if (user == null) return;
        for (Notification n : notificationRepository.findInboxForUser(user.getId())) {
            if (n.getReadAt() == null) {
                n.setReadAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
        }
    }

    // ── Recherche / admin ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<NotificationDto> search(Long userId, String status, String type) {
        return notificationRepository.search(userId, status, type).stream().map(this::toDto).toList();
    }

    // ── Test manuel (ADMIN) ──────────────────────────────────────────────────────
    /** Queue a one-off test message and drain immediately so the result is visible. */
    public NotificationDto sendTest(String channel, String recipient, String body) {
        String ch = channel != null && !channel.isBlank() ? channel.trim().toUpperCase() : "SMS";
        String text = body != null && !body.isBlank() ? body
                : "[" + clinicName() + "] Message de test ClinicApp.";
        Notification n = enqueue("SYSTEM", ch, null, null, recipient, "Test", text, null);
        if (n == null) throw new IllegalArgumentException("Destinataire ou message manquant");
        dispatch(n);
        return toDto(notificationRepository.findWithRefsById(n.getId()).orElse(n));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private String defaultRecipient(String channel, User user, Patient patient) {
        if ("IN_APP".equals(channel)) {
            return user != null ? user.getUsername() : null;
        }
        if ("EMAIL".equals(channel)) {
            if (patient != null && patient.getEmail() != null) return patient.getEmail();
            return clinicEmail();
        }
        // SMS
        if (patient != null && patient.getPhone() != null) return patient.getPhone();
        return null;
    }

    private ClinicConfig config() { return clinicConfigService.getConfig(); }
    private String clinicName()  { ClinicConfig c = config(); return c != null && c.getName() != null ? c.getName() : "CLINIQUE"; }
    private String clinicPhone() { ClinicConfig c = config(); return c != null && c.getPhone() != null ? c.getPhone() : ""; }
    private String clinicEmail() { ClinicConfig c = config(); return c != null ? c.getEmail() : null; }
    private String currency()    { ClinicConfig c = config(); return c != null && c.getCurrency() != null ? c.getCurrency() : "XOF"; }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    public NotificationDto toDto(Notification n) {
        NotificationDto dto = new NotificationDto();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setChannel(n.getChannel());
        dto.setRecipient(n.getRecipient());
        dto.setSubject(n.getSubject());
        dto.setBody(n.getBody());
        dto.setStatus(n.getStatus());
        dto.setReadAt(n.getReadAt());
        dto.setScheduledAt(n.getScheduledAt());
        dto.setSentAt(n.getSentAt());
        dto.setErrorMessage(n.getErrorMessage());
        dto.setCreatedAt(n.getCreatedAt());
        if (n.getUser() != null) {
            dto.setUserId(n.getUser().getId());
            dto.setUserName(n.getUser().getFullName());
        }
        if (n.getPatient() != null) {
            dto.setPatientId(n.getPatient().getId());
            dto.setPatientName(n.getPatient().getFullName());
        }
        return dto;
    }
}
