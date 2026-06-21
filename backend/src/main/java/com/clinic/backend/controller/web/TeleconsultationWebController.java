package com.clinic.backend.controller.web;

import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.portal.PortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Salle de téléconsultation (P3.7) — page de jonction partagée par le personnel
 * <b>et</b> le patient. Mise en page autonome (ni sidebar staff, ni portail) pour
 * fonctionner identiquement des deux côtés.
 * <p>
 * Contrôle d'accès : personnel soignant gérant l'agenda (ADMIN/MEDECIN/INFIRMIER/
 * SECRETAIRE) <b>ou</b> le patient propriétaire du rendez-vous. La salle visio
 * elle-même est protégée par un identifiant non devinable.
 */
@Controller
@RequestMapping("/teleconsultation")
@RequiredArgsConstructor
public class TeleconsultationWebController {

    private static final Set<String> STAFF_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_MEDECIN", "ROLE_INFIRMIER", "ROLE_SECRETAIRE");

    private final AppointmentService appointmentService;
    private final PortalService portalService;

    @GetMapping("/{id}")
    public String room(@PathVariable Long id, Model model) {
        AppointmentDto appt = appointmentService.getDtoById(id);
        authorize(appt);
        model.addAttribute("appointment", appt);
        return "teleconsultation/room";
    }

    /** Autorise le personnel soignant ou le patient propriétaire ; sinon 403. */
    private void authorize(AppointmentDto appt) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        if (roles.stream().anyMatch(STAFF_ROLES::contains)) return;

        if (roles.contains("ROLE_PATIENT")) {
            Patient me = portalService.currentPatient();
            if (me != null && me.getId().equals(appt.getPatientId())) return;
        }
        throw new AccessDeniedException("Accès à la téléconsultation non autorisé");
    }
}
