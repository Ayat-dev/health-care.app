package com.clinic.backend.controller.web;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.i18n.WebI18n;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/appointments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')") // PHI — ADMIN retiré (P6)
public class AppointmentWebController {

    private final AppointmentService appointmentService;
    private final PatientService patientService;
    private final UserRepository userRepository;
    private final com.clinic.backend.export.FicheExportService ficheExportService;
    private final WebI18n i18n;

    // ── Vue jour (liste) ──────────────────────────────────────────────────
    @GetMapping
    public String day(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(required = false) Long doctorId,
                      Model model) {
        LocalDate d = date != null ? date : LocalDate.now();
        List<Appointment> appointments = appointmentService.findForDay(d, doctorId);

        model.addAttribute("appointments", appointments);
        model.addAttribute("date", d);
        model.addAttribute("prevDay", d.minusDays(1));
        model.addAttribute("nextDay", d.plusDays(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("doctors", doctors());
        model.addAttribute("selectedDoctorId", doctorId);
        return "appointments/list";
    }

    // ── Aperçu de l'export agenda (choix des colonnes) ─────────────────────────
    @GetMapping("/export/preview")
    public String exportPreview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) java.util.List<String> cols,
            Model model) {
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        java.util.Map<String, String> ctx = new java.util.LinkedHashMap<>();
        ctx.put("from", f.toString());
        ctx.put("to", t.toString());
        ctx.put("doctorId", doctorId != null ? doctorId.toString() : null);
        ctx.put("status", status);
        model.addAttribute("preview", ficheExportService.preview(
                i18n.t("appointments.list.heading"), "/appointments/export/preview", "/appointments/export",
                ctx, com.clinic.backend.export.FicheExportService.APPOINTMENT_COLS, dtos(f, t, doctorId, status),
                toSet(cols)));
        return "export/preview";
    }

    // ── Export Excel de l'agenda (plage de dates) — colonnes filtrables ────────
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) java.util.List<String> cols) {
        LocalDate f = from != null ? from : LocalDate.now();
        LocalDate t = to != null ? to : f;
        byte[] xlsx = ficheExportService.xlsx("Rendez-vous",
                com.clinic.backend.export.FicheExportService.APPOINTMENT_COLS,
                dtos(f, t, doctorId, status), toSet(cols));
        return ReportWebController.xlsxAttachment(xlsx, "rendez-vous-" + f + "_" + t + ".xlsx");
    }

    private List<AppointmentDto> dtos(LocalDate f, LocalDate t, Long doctorId, String status) {
        return appointmentService.search(f, t, doctorId, null, status)
                .stream().map(appointmentService::toDto).toList();
    }

    private static java.util.Set<String> toSet(java.util.List<String> cols) {
        return cols != null ? new java.util.LinkedHashSet<>(cols) : null;
    }

    // ── Vue semaine ───────────────────────────────────────────────────────
    @GetMapping("/week")
    public String week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false) Long doctorId,
                       Model model) {
        LocalDate ref = date != null ? date : LocalDate.now();
        LocalDate weekStart = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(weekStart.plusDays(i));

        // Créneaux de 30 min de 07:00 à 19:30
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime t = LocalTime.of(7, 0); t.isBefore(LocalTime.of(20, 0)); t = t.plusMinutes(30)) {
            slots.add(t);
        }

        // cellMap : "yyyy-MM-dd_HH:mm" → liste de RDV démarrant dans ce créneau
        List<Appointment> appointments = appointmentService.findForWeek(weekStart, doctorId);
        Map<String, List<Appointment>> cellMap = new LinkedHashMap<>();
        for (Appointment a : appointments) {
            LocalDate ad = a.getStartTime().toLocalDate();
            LocalTime at = a.getStartTime().toLocalTime();
            // arrondi au créneau de 30 min inférieur
            LocalTime slot = LocalTime.of(at.getHour(), at.getMinute() < 30 ? 0 : 30);
            String key = ad + "_" + slot;
            cellMap.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("days", days);
        model.addAttribute("slots", slots);
        model.addAttribute("cellMap", cellMap);
        model.addAttribute("prevWeek", weekStart.minusWeeks(1));
        model.addAttribute("nextWeek", weekStart.plusWeeks(1));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("doctors", doctors());
        model.addAttribute("selectedDoctorId", doctorId);
        return "appointments/week";
    }

    // ── Formulaire création ───────────────────────────────────────────────
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Model model) {
        AppointmentDto dto = new AppointmentDto();
        dto.setStatus("PLANIFIE");
        model.addAttribute("appointment", dto);
        populateFormOptions(model);
        return "appointments/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute AppointmentDto dto, RedirectAttributes ra, Model model) {
        try {
            Appointment created = appointmentService.create(dto);
            ra.addFlashAttribute("success", i18n.t("appointments.flash.created"));
            return "redirect:/appointments?date=" + created.getStartTime().toLocalDate();
        } catch (IllegalArgumentException e) {
            model.addAttribute("appointment", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "appointments/form";
        }
    }

    // ── Rejeu d'un RDV créé hors-ligne (B4, PWA) ──────────────────────────
    // Appelé par js/offline-sync.js au retour réseau, depuis le contexte page (cookie de
    // session + jeton CSRF en en-tête). Idempotent via l'en-tête Idempotency-Key : un rejeu
    // de la même clé retombe sur le RDV déjà créé (pas de doublon). Reste sur la chaîne web
    // (session) — surface minimale, pas de JWT à gérer côté navigateur.
    @PostMapping(value = "/offline", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> syncOffline(
            @RequestBody AppointmentDto dto,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        try {
            Appointment a = appointmentService.createIdempotent(dto, idempotencyKey);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "id", a.getId(),
                    "requestKey", idempotencyKey));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Conflit métier (créneau pris, données invalides…) : 409 → le client marque
            // l'item en ÉCHEC et le conserve pour révision (décision B4), sans rejeu en boucle.
            return ResponseEntity.status(409).body(Map.of(
                    "status", "error",
                    "message", e.getMessage() != null ? e.getMessage() : "Conflit",
                    "requestKey", idempotencyKey));
        }
    }

    // ── Formulaire modification ───────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("appointment", appointmentService.getDtoById(id));
        populateFormOptions(model);
        return "appointments/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute AppointmentDto dto,
                         RedirectAttributes ra, Model model) {
        try {
            Appointment updated = appointmentService.update(id, dto);
            ra.addFlashAttribute("success", i18n.t("appointments.flash.updated"));
            return "redirect:/appointments?date=" + updated.getStartTime().toLocalDate();
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("appointment", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "appointments/form";
        }
    }

    // ── Actions de statut (web) ───────────────────────────────────────────
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable Long id, RedirectAttributes ra) {
        appointmentService.confirm(id);
        ra.addFlashAttribute("success", i18n.t("appointments.flash.confirmed"));
        return redirectToDay(id);
    }

    @PostMapping("/{id}/start")
    public String start(@PathVariable Long id, RedirectAttributes ra) {
        appointmentService.start(id);
        ra.addFlashAttribute("success", i18n.t("appointments.flash.started"));
        return redirectToDay(id);
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        appointmentService.complete(id);
        ra.addFlashAttribute("success", i18n.t("appointments.flash.completed"));
        return redirectToDay(id);
    }

    @PostMapping("/{id}/absent")
    public String absent(@PathVariable Long id, RedirectAttributes ra) {
        appointmentService.markAbsent(id);
        ra.addFlashAttribute("success", i18n.t("appointments.flash.absent"));
        return redirectToDay(id);
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        appointmentService.cancel(id, reason);
        ra.addFlashAttribute("success", i18n.t("appointments.flash.cancelled"));
        return redirectToDay(id);
    }

    // ── Télémédecine (P3.7) ────────────────────────────────────────────────
    @PostMapping("/{id}/teleconsultation")
    public String enableTeleconsultation(@PathVariable Long id, RedirectAttributes ra) {
        try {
            appointmentService.enableTeleconsultation(id);
            ra.addFlashAttribute("success", i18n.t("appointments.flash.teleconsultation_enabled"));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return redirectToDay(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private String redirectToDay(Long id) {
        LocalDate d = appointmentService.getById(id).getStartTime().toLocalDate();
        return "redirect:/appointments?date=" + d;
    }

    private void populateFormOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("doctors", doctors());
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }
}
