package com.clinic.backend.controller.web;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientOverviewService;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Interface web Thymeleaf pour la gestion des patients.
 *
 * Règles d'accès (PHI — ADMIN/OWNER exclus depuis P6) :
 *  - Lecture (liste, détail) : MEDECIN, INFIRMIER, SECRETAIRE, PHARMACIEN, LABORANTIN, CAISSIER
 *  - Création / modification  : MEDECIN, SECRETAIRE, INFIRMIER
 *  - Suppression (soft delete) : MEDECIN uniquement, via l'API — pas d'endpoint web
 */
@Controller
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientWebController {

    private final PatientService patientService;
    private final PatientOverviewService patientOverviewService;
    private final UserRepository userRepository;
    private final com.clinic.backend.consultation.ConsultationService consultationService;
    private final com.clinic.backend.lab.LabService labService;
    private final com.clinic.backend.radiology.RadiologyService radiologyService;
    private final com.clinic.backend.hospitalization.HospitalizationService hospitalizationService;
    private final com.clinic.backend.maternity.MaternityService maternityService;
    private final com.clinic.backend.billing.BillingService billingService;
    private final com.clinic.backend.export.FicheExportService ficheExportService;
    private final com.clinic.backend.portal.PortalAccountService portalAccountService;
    private final com.clinic.backend.i18n.WebI18n i18n;

    @GetMapping
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Patient> patients = patientService.search(q, page, 20);
        model.addAttribute("patients", patients);
        model.addAttribute("q", q);
        model.addAttribute("currentPage", page);
        return "patients/list";
    }

    // ── Aperçu de l'export registre patients (choix des colonnes) ────────────────
    @GetMapping("/export/preview")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public String exportPreview(@RequestParam(defaultValue = "") String q,
                                @RequestParam(required = false) java.util.List<String> cols, Model model) {
        java.util.Map<String, String> ctx = new java.util.LinkedHashMap<>();
        ctx.put("q", q);
        model.addAttribute("preview", ficheExportService.preview(
                i18n.t("patients.list.heading"), "/patients/export/preview", "/patients/export",
                ctx, com.clinic.backend.export.FicheExportService.PATIENT_COLS,
                patientService.search(q, 0, 5000).getContent(), toSet(cols)));
        return "export/preview";
    }

    // ── Export Excel du registre patients (PHI — mêmes rôles que la liste) ────────
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) java.util.List<String> cols) {
        byte[] xlsx = ficheExportService.xlsx("Patients",
                com.clinic.backend.export.FicheExportService.PATIENT_COLS,
                patientService.search(q, 0, 5000).getContent(), toSet(cols));
        return ReportWebController.xlsxAttachment(xlsx, "registre-patients.xlsx");
    }

    private static java.util.Set<String> toSet(java.util.List<String> cols) {
        return cols != null ? new java.util.LinkedHashSet<>(cols) : null;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public String detail(@PathVariable Long id, Model model) {
        Patient patient = patientService.getByIdWithDoctor(id);

        var consultations = consultationService.findForPatient(id);
        var labRequests = labService.findForPatient(id);
        var radiologyRequests = radiologyService.findForPatient(id);
        var hospitalizations = hospitalizationService.findForPatient(id);
        var invoices = billingService.findForPatient(id);
        // Dossier maternité — pertinent uniquement pour les patientes.
        var maternityRecord = "F".equalsIgnoreCase(patient.getGender())
                ? maternityService.findForPatient(id) : null;

        model.addAttribute("patient", patient);
        model.addAttribute("consultations", consultations);
        model.addAttribute("labRequests", labRequests);
        model.addAttribute("radiologyRequests", radiologyRequests);
        model.addAttribute("hospitalizations", hospitalizations);
        model.addAttribute("invoices", invoices);
        if (maternityRecord != null) {
            model.addAttribute("maternityRecord", maternityRecord);
        }

        // Coup d'œil + timeline (P3.6) — agrégat en mémoire, zéro requête de plus.
        model.addAttribute("overview", patientOverviewService.build(
                patient, consultations, labRequests, radiologyRequests,
                hospitalizations, invoices, maternityRecord));

        // État du compte portail (accès patient à son espace).
        model.addAttribute("portalStatus", portalAccountService.status(id));

        return "patients/detail";
    }

    // ── Compte portail patient (onboarding) — front-desk / clinicien ─────────────
    @PostMapping("/{id}/portal/activate")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE')")
    public String activatePortal(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var creds = portalAccountService.activate(id);
            ra.addFlashAttribute("portalUsername", creds.username());
            ra.addFlashAttribute("portalPassword", creds.tempPassword());
            ra.addFlashAttribute("success", i18n.t("patients.portal.activated"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patients/" + id;
    }

    @PostMapping("/{id}/portal/reset")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE')")
    public String resetPortalPassword(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var creds = portalAccountService.resetPassword(id);
            ra.addFlashAttribute("portalUsername", creds.username());
            ra.addFlashAttribute("portalPassword", creds.tempPassword());
            ra.addFlashAttribute("success", i18n.t("patients.portal.reset_done"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patients/" + id;
    }

    @PostMapping("/{id}/portal/toggle")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE')")
    public String togglePortal(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes ra) {
        try {
            portalAccountService.setActive(id, active);
            ra.addFlashAttribute("success", i18n.t(active ? "patients.portal.reactivated" : "patients.portal.deactivated"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patients/" + id;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String newForm(Model model) {
        model.addAttribute("patient", new PatientDto());
        model.addAttribute("doctors", userRepository.findAll());
        return "patients/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String create(@ModelAttribute PatientDto dto) {
        Patient created = patientService.create(dto);
        return "redirect:/patients/" + created.getId();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("patient", patientService.toDto(patientService.getById(id)));
        model.addAttribute("doctors", userRepository.findAll());
        return "patients/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String update(@PathVariable Long id, @ModelAttribute PatientDto dto) {
        patientService.update(id, dto);
        return "redirect:/patients/" + id;
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String uploadPhoto(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              RedirectAttributes ra) {
        try {
            patientService.uploadPhoto(id, file);
            ra.addFlashAttribute("success", i18n.t("patients.flash.photo_updated"));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patients/" + id;
    }
}
