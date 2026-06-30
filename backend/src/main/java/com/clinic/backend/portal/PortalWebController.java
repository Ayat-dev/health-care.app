package com.clinic.backend.portal;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.billing.BillingService;
import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.consultation.ConsultationService;
import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.i18n.WebI18n;
import com.clinic.backend.lab.LabService;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.radiology.RadiologyService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Portail patient (P2.4) — espace en lecture pour le rôle {@code PATIENT}.
 * <p>
 * Un patient connecté ne voit que son propre dossier (résolu via {@link PortalService}) :
 * ses rendez-vous, l'historique de ses consultations/analyses, ses factures. Il peut
 * demander un nouveau rendez-vous (créé en statut {@code PLANIFIE}, à confirmer par l'accueil).
 * <p>
 * Mise en page dédiée ({@code portal/layout}) — aucune sidebar staff.
 */
@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PortalWebController {

    private final PortalService portalService;
    private final PortalDocumentService portalDocumentService;
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final com.clinic.backend.consultation.PrescriptionService prescriptionService;
    private final LabService labService;
    private final RadiologyService radiologyService;
    private final BillingService billingService;
    private final com.clinic.backend.service.UserService userService;
    private final UserRepository userRepository;
    private final WebI18n i18n;

    // ── Accueil ────────────────────────────────────────────────────────────
    @GetMapping
    public String home(Model model) {
        Patient patient = portalService.currentPatient();
        model.addAttribute("patient", patient);

        List<AppointmentDto> upcoming = appointmentsFor(patient).stream()
                .filter(a -> a.getStartTime() != null && a.getStartTime().isAfter(LocalDateTime.now())
                        && !"ANNULE".equals(a.getStatus()))
                .sorted(Comparator.comparing(AppointmentDto::getStartTime))
                .limit(3)
                .toList();
        model.addAttribute("upcoming", upcoming);
        return "portal/home";
    }

    // ── Rendez-vous ──────────────────────────────────────────────────────────
    @GetMapping("/appointments")
    public String appointments(Model model) {
        Patient patient = portalService.currentPatient();
        model.addAttribute("patient", patient);
        model.addAttribute("appointments", appointmentsFor(patient).stream()
                .sorted(Comparator.comparing(AppointmentDto::getStartTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList());
        return "portal/appointments";
    }

    @GetMapping("/appointments/request")
    public String requestForm(Model model) {
        model.addAttribute("patient", portalService.currentPatient());
        model.addAttribute("appointment", new AppointmentDto());
        model.addAttribute("doctors", doctors());
        return "portal/appointment-form";
    }

    @PostMapping("/appointments/request")
    public String submitRequest(@ModelAttribute AppointmentDto dto,
                                RedirectAttributes ra, Model model) {
        Patient patient = portalService.currentPatient();
        // Le patient ne peut demander un RDV que pour lui-même : on force le patientId.
        dto.setPatientId(patient.getId());
        dto.setStatus("PLANIFIE");
        try {
            appointmentService.create(dto);
            ra.addFlashAttribute("success", i18n.t("portal.flash.requested"));
            return "redirect:/portal/appointments";
        } catch (IllegalArgumentException e) {
            model.addAttribute("patient", patient);
            model.addAttribute("appointment", dto);
            model.addAttribute("doctors", doctors());
            model.addAttribute("error", e.getMessage());
            return "portal/appointment-form";
        }
    }

    // ── Annulation d'un rendez-vous (cloisonnée au dossier) ──────────────────
    @PostMapping("/appointments/{id}/cancel")
    public String cancelAppointment(@PathVariable Long id, RedirectAttributes ra) {
        Patient patient = portalService.currentPatient();
        AppointmentDto dto = appointmentService.getDtoById(id);
        // Cloisonnement : le patient ne peut annuler que ses propres rendez-vous.
        if (dto.getPatientId() == null || !dto.getPatientId().equals(patient.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Ce rendez-vous n'appartient pas à votre dossier.");
        }
        // Seuls les RDV non encore honorés/clos sont annulables par le patient.
        if (!"PLANIFIE".equals(dto.getStatus()) && !"CONFIRME".equals(dto.getStatus())) {
            ra.addFlashAttribute("error", i18n.t("portal.flash.cancel_too_late"));
            return "redirect:/portal/appointments";
        }
        appointmentService.cancel(id, i18n.t("portal.cancel.reason"));
        ra.addFlashAttribute("success", i18n.t("portal.flash.cancelled"));
        return "redirect:/portal/appointments";
    }

    // ── Mon dossier (lecture seule) ──────────────────────────────────────────
    @GetMapping("/record")
    public String record(Model model) {
        Patient patient = portalService.currentPatient();
        var consultations = consultationService.findForPatient(patient.getId());
        model.addAttribute("patient", patient);
        model.addAttribute("consultations", consultations);
        model.addAttribute("labRequests", labService.findForPatient(patient.getId()));
        model.addAttribute("radiologyRequests", radiologyService.findForPatient(patient.getId()));
        model.addAttribute("invoices", billingService.findForPatient(patient.getId()));
        // Ordonnances : résolues par consultation (pas de lookup direct par patient).
        List<com.clinic.backend.dto.PrescriptionDto> prescriptions = consultations.stream()
                .map(c -> prescriptionService.findDtoForConsultation(c.getId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        model.addAttribute("prescriptions", prescriptions);
        return "portal/record";
    }

    // ── Téléchargements PDF (cloisonnés au dossier — D4b) ─────────────────────
    @GetMapping("/lab/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> labPdf(@PathVariable Long id) {
        return com.clinic.backend.controller.web.BillingWebController.pdfInline(
                portalDocumentService.labBulletinPdf(id), "bulletin-labo-" + id + ".pdf");
    }

    @GetMapping("/radiology/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> radiologyPdf(@PathVariable Long id) {
        return com.clinic.backend.controller.web.BillingWebController.pdfInline(
                portalDocumentService.radiologyBulletinPdf(id), "compte-rendu-" + id + ".pdf");
    }

    @GetMapping("/prescriptions/{id}/pdf")
    public org.springframework.http.ResponseEntity<byte[]> prescriptionPdf(@PathVariable Long id) {
        return com.clinic.backend.controller.web.BillingWebController.pdfInline(
                portalDocumentService.prescriptionPdf(id), "ordonnance-" + id + ".pdf");
    }

    @GetMapping("/invoices/{id}/receipt/pdf")
    public org.springframework.http.ResponseEntity<byte[]> receiptPdf(@PathVariable Long id) {
        return com.clinic.backend.controller.web.BillingWebController.pdfInline(
                portalDocumentService.receiptPdf(id), "recu-" + id + ".pdf");
    }

    // ── Profil / mot de passe ─────────────────────────────────────────────────
    @GetMapping("/profile")
    public String profile(Model model,
                          @org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        model.addAttribute("patient", portalService.currentPatient());
        model.addAttribute("username", user != null ? user.getUsername() : "");
        return "portal/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@org.springframework.security.core.annotation.AuthenticationPrincipal User user,
                                 @org.springframework.web.bind.annotation.RequestParam String currentPassword,
                                 @org.springframework.web.bind.annotation.RequestParam String newPassword,
                                 @org.springframework.web.bind.annotation.RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", i18n.t("portal.flash.password_mismatch"));
            return "redirect:/portal/profile";
        }
        try {
            userService.changeOwnPassword(user.getId(), currentPassword, newPassword);
            ra.addFlashAttribute("success", i18n.t("portal.flash.password_changed"));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/profile";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /** RDV du patient mappés en DTO (le {@code search} join-fetch patient/médecin/département). */
    private List<AppointmentDto> appointmentsFor(Patient patient) {
        return appointmentService.search(null, null, null, patient.getId(), null).stream()
                .map(appointmentService::toDto)
                .toList();
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }

    /** Compte portail non rattaché à un dossier patient — page d'information dédiée. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public String noLinkedRecord(ResourceNotFoundException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "portal/no-record";
    }
}
