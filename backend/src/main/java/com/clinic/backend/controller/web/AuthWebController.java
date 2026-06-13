package com.clinic.backend.controller.web;

import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.billing.InvoiceRepository;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final InvoiceRepository invoiceRepository;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("patientsCount", patientRepository.countActive());
        model.addAttribute("appointmentsToday", appointmentRepository.countBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        model.addAttribute("consultationsToday", consultationRepository.countBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        model.addAttribute("pendingInvoices",
                invoiceRepository.countByStatus("EN_ATTENTE") + invoiceRepository.countByStatus("PARTIEL"));
        return "dashboard";
    }
}
