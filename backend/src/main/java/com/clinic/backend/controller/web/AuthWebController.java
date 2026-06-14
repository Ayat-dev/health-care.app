package com.clinic.backend.controller.web;

import com.clinic.backend.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Contrôleur web pour login/dashboard.
 *
 * Les compteurs RDV, consultations, factures seront câblés quand les modules
 * correspondants seront implémentés (stubs à 0 en attendant).
 */
@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final PatientRepository patientRepository;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("patientsCount", patientRepository.countActive());
        // Stubs — seront remplacés module par module
        model.addAttribute("appointmentsToday", 0);
        model.addAttribute("consultationsToday", 0);
        model.addAttribute("pendingInvoices", 0);
        return "dashboard";
    }
}
