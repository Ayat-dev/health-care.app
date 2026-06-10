package com.clinic.backend.controller.web;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationService;
import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.department.DepartmentService;
import com.clinic.backend.dto.ConsultationDto;
import com.clinic.backend.dto.PrescriptionDto;
import com.clinic.backend.dto.PrescriptionItemDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/consultations")
@RequiredArgsConstructor
public class ConsultationWebController {

    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;
    private final PatientService patientService;
    private final DepartmentService departmentService;
    private final UserRepository userRepository;

    // ── Liste ───────────────────────────────────────────────────────────────
    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long doctorId,
                       @RequestParam(required = false) String status,
                       Model model) {
        List<Consultation> consultations = consultationService.search(from, to, doctorId, null, status);
        model.addAttribute("consultations", consultations);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status);
        model.addAttribute("doctors", doctors());
        model.addAttribute("selectedDoctorId", doctorId);
        return "consultations/list";
    }

    // ── Détail / fiche ────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("consultation", consultationService.getDtoById(id));
        model.addAttribute("prescription", prescriptionService.findDtoForConsultation(id));
        return "consultations/detail";
    }

    // ── Formulaire création ───────────────────────────────────────────────────
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long appointmentId,
                          @RequestParam(required = false) Long patientId,
                          Model model) {
        ConsultationDto dto = consultationService.prefillFromAppointment(appointmentId);
        if (patientId != null && dto.getPatientId() == null) {
            dto.setPatientId(patientId);
        }
        model.addAttribute("consultation", dto);
        populateFormOptions(model);
        return "consultations/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute ConsultationDto dto, RedirectAttributes ra, Model model) {
        try {
            Consultation created = consultationService.create(dto);
            ra.addFlashAttribute("success", "Consultation enregistrée.");
            return "redirect:/consultations/" + created.getId();
        } catch (RuntimeException e) {
            model.addAttribute("consultation", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "consultations/form";
        }
    }

    // ── Formulaire modification ───────────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("consultation", consultationService.getDtoById(id));
        populateFormOptions(model);
        return "consultations/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute ConsultationDto dto,
                         RedirectAttributes ra, Model model) {
        try {
            consultationService.update(id, dto);
            ra.addFlashAttribute("success", "Consultation mise à jour.");
            return "redirect:/consultations/" + id;
        } catch (RuntimeException e) {
            dto.setId(id);
            model.addAttribute("consultation", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "consultations/form";
        }
    }

    // ── Actions de statut ─────────────────────────────────────────────────────
    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            consultationService.complete(id);
            ra.addFlashAttribute("success", "Consultation clôturée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/consultations/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        consultationService.cancel(id);
        ra.addFlashAttribute("success", "Consultation annulée.");
        return "redirect:/consultations/" + id;
    }

    // ── Ordonnance ─────────────────────────────────────────────────────────────
    @GetMapping("/{id}/prescription")
    public String prescriptionForm(@PathVariable Long id, Model model) {
        ConsultationDto consultation = consultationService.getDtoById(id);
        PrescriptionDto prescription = prescriptionService.findDtoForConsultation(id);
        if (prescription == null) {
            prescription = new PrescriptionDto();
            prescription.setConsultationId(id);
            prescription.setIssueDate(LocalDate.now());
            // Démarre avec quelques lignes vides pour la saisie
            for (int i = 0; i < 3; i++) prescription.getItems().add(new PrescriptionItemDto());
        }
        model.addAttribute("consultation", consultation);
        model.addAttribute("prescription", prescription);
        return "prescriptions/form";
    }

    @PostMapping("/{id}/prescription")
    public String savePrescription(@PathVariable("id") Long consultationId, @ModelAttribute PrescriptionDto dto,
                                   RedirectAttributes ra, Model model) {
        // NB: don't trust dto.getId() here — the {id} path variable (the consultation id)
        // is bound into the DTO's same-named field. Branch on the existing ordonnance instead.
        try {
            PrescriptionDto existing = prescriptionService.findDtoForConsultation(consultationId);
            if (existing != null) {
                prescriptionService.update(existing.getId(), dto);
            } else {
                prescriptionService.createForConsultation(consultationId, dto);
            }
            ra.addFlashAttribute("success", "Ordonnance enregistrée.");
            return "redirect:/consultations/" + consultationId;
        } catch (RuntimeException e) {
            model.addAttribute("consultation", consultationService.getDtoById(consultationId));
            model.addAttribute("prescription", dto);
            model.addAttribute("error", e.getMessage());
            return "prescriptions/form";
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private void populateFormOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("doctors", doctors());
        model.addAttribute("departments", departmentService.listActive());
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }
}
