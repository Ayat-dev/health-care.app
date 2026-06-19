package com.clinic.backend.portal;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.billing.BillingService;
import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.consultation.ConsultationService;
import com.clinic.backend.dto.AppointmentDto;
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
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final LabService labService;
    private final RadiologyService radiologyService;
    private final BillingService billingService;
    private final UserRepository userRepository;

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
            ra.addFlashAttribute("success",
                    "Votre demande de rendez-vous a été enregistrée. L'accueil la confirmera prochainement.");
            return "redirect:/portal/appointments";
        } catch (IllegalArgumentException e) {
            model.addAttribute("patient", patient);
            model.addAttribute("appointment", dto);
            model.addAttribute("doctors", doctors());
            model.addAttribute("error", e.getMessage());
            return "portal/appointment-form";
        }
    }

    // ── Mon dossier (lecture seule) ──────────────────────────────────────────
    @GetMapping("/record")
    public String record(Model model) {
        Patient patient = portalService.currentPatient();
        model.addAttribute("patient", patient);
        model.addAttribute("consultations", consultationService.findForPatient(patient.getId()));
        model.addAttribute("labRequests", labService.findForPatient(patient.getId()));
        model.addAttribute("radiologyRequests", radiologyService.findForPatient(patient.getId()));
        model.addAttribute("invoices", billingService.findForPatient(patient.getId()));
        return "portal/record";
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
