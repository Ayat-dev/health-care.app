package com.clinic.backend.controller.web;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientWebController {

    private final PatientService patientService;
    private final UserRepository userRepository;
    private final com.clinic.backend.consultation.ConsultationService consultationService;
    private final com.clinic.backend.lab.LabService labService;
    private final com.clinic.backend.radiology.RadiologyService radiologyService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Patient> patients = patientService.search(q, page, 20);
        model.addAttribute("patients", patients);
        model.addAttribute("q", q);
        model.addAttribute("currentPage", page);
        return "patients/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Patient patient = patientService.getByIdWithDoctor(id);
        model.addAttribute("patient", patient);
        model.addAttribute("consultations", consultationService.findForPatient(id));
        model.addAttribute("labRequests", labService.findForPatient(id));
        model.addAttribute("radiologyRequests", radiologyService.findForPatient(id));
        return "patients/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("patient", new PatientDto());
        model.addAttribute("doctors", userRepository.findAll());
        return "patients/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute PatientDto dto) {
        Patient created = patientService.create(dto);
        return "redirect:/patients/" + created.getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("patient", patientService.toDto(patientService.getById(id)));
        model.addAttribute("doctors", userRepository.findAll());
        return "patients/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute PatientDto dto) {
        patientService.update(id, dto);
        return "redirect:/patients/" + id;
    }

    @PostMapping("/{id}/photo")
    public String uploadPhoto(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              RedirectAttributes ra) {
        try {
            patientService.uploadPhoto(id, file);
            ra.addFlashAttribute("success", "Photo du patient mise à jour.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patients/" + id;
    }
}
