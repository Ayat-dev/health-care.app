package com.clinic.backend.controller.web;

import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.dto.HospitalizationDto;
import com.clinic.backend.dto.RoomDto;
import com.clinic.backend.hospitalization.Hospitalization;
import com.clinic.backend.hospitalization.HospitalizationService;
import com.clinic.backend.hospitalization.RoomService;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/hospitalization")
@RequiredArgsConstructor
public class HospitalizationWebController {

    private final HospitalizationService hospitalizationService;
    private final RoomService roomService;
    private final PatientService patientService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    // ── Plan des lits ─────────────────────────────────────────────────────────
    @GetMapping
    public String beds(Model model) {
        model.addAttribute("board", hospitalizationService.bedBoard());
        return "hospitalization/beds";
    }

    // ── Liste des séjours ──────────────────────────────────────────────────────
    @GetMapping("/list")
    public String list(@RequestParam(required = false) String status, Model model) {
        List<Hospitalization> stays = hospitalizationService.search(status);
        model.addAttribute("stays", stays.stream().map(hospitalizationService::toDto).toList());
        model.addAttribute("status", status);
        return "hospitalization/list";
    }

    // ── Détail d'un séjour ──────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("stay", hospitalizationService.getDtoById(id));
        model.addAttribute("rooms", hospitalizationService.availableRooms());
        return "hospitalization/detail";
    }

    // ── Admission ────────────────────────────────────────────────────────────────
    @GetMapping("/admit")
    public String admitForm(@RequestParam(required = false) Long patientId,
                            @RequestParam(required = false) Long consultationId,
                            @RequestParam(required = false) Long roomId,
                            Model model) {
        model.addAttribute("stay", hospitalizationService.prefillAdmission(patientId, consultationId, roomId));
        populateAdmitOptions(model);
        return "hospitalization/admit-form";
    }

    @PostMapping("/admit")
    public String admit(@ModelAttribute HospitalizationDto dto, RedirectAttributes ra, Model model) {
        try {
            Hospitalization created = hospitalizationService.admit(dto);
            ra.addFlashAttribute("success", "Patient admis.");
            return "redirect:/hospitalization/" + created.getId();
        } catch (RuntimeException e) {
            model.addAttribute("stay", dto);
            model.addAttribute("error", e.getMessage());
            populateAdmitOptions(model);
            return "hospitalization/admit-form";
        }
    }

    // ── Transfert ──────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/transfer")
    public String transfer(@PathVariable Long id,
                           @RequestParam Long newRoomId,
                           @RequestParam(required = false) String reason,
                           RedirectAttributes ra) {
        try {
            Hospitalization next = hospitalizationService.transfer(id, newRoomId, reason);
            ra.addFlashAttribute("success", "Patient transféré.");
            return "redirect:/hospitalization/" + next.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/hospitalization/" + id;
        }
    }

    // ── Sortie ─────────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/discharge")
    public String discharge(@PathVariable Long id,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String diagnosis,
                            RedirectAttributes ra) {
        try {
            hospitalizationService.discharge(id, status, diagnosis);
            ra.addFlashAttribute("success", "Sortie enregistrée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/hospitalization/" + id;
    }

    // ── Chambres (référentiel) ──────────────────────────────────────────────────────
    @GetMapping("/rooms")
    public String rooms(Model model) {
        model.addAttribute("rooms", roomService.listAll());
        return "hospitalization/rooms";
    }

    @GetMapping("/rooms/new")
    public String newRoomForm(Model model) {
        model.addAttribute("room", new RoomDto());
        model.addAttribute("departments", departments());
        return "hospitalization/room-form";
    }

    @PostMapping("/rooms/new")
    public String createRoom(@ModelAttribute RoomDto dto, RedirectAttributes ra, Model model) {
        try {
            roomService.create(dto);
            ra.addFlashAttribute("success", "Chambre créée.");
            return "redirect:/hospitalization/rooms";
        } catch (RuntimeException e) {
            model.addAttribute("room", dto);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("departments", departments());
            return "hospitalization/room-form";
        }
    }

    @GetMapping("/rooms/{id}/edit")
    public String editRoomForm(@PathVariable Long id, Model model) {
        model.addAttribute("room", roomService.getDtoById(id));
        model.addAttribute("departments", departments());
        return "hospitalization/room-form";
    }

    @PostMapping("/rooms/{id}/edit")
    public String updateRoom(@PathVariable Long id, @ModelAttribute RoomDto dto,
                             RedirectAttributes ra, Model model) {
        try {
            roomService.update(id, dto);
            ra.addFlashAttribute("success", "Chambre mise à jour.");
            return "redirect:/hospitalization/rooms";
        } catch (RuntimeException e) {
            dto.setId(id);
            model.addAttribute("room", dto);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("departments", departments());
            return "hospitalization/room-form";
        }
    }

    @PostMapping("/rooms/{id}/toggle")
    public String toggleRoom(@PathVariable Long id, RedirectAttributes ra) {
        try {
            roomService.toggleActive(id);
            ra.addFlashAttribute("success", "Statut de la chambre mis à jour.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/hospitalization/rooms";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────
    private void populateAdmitOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("doctors", doctors());
        model.addAttribute("rooms", hospitalizationService.availableRooms());
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }

    private List<Department> departments() {
        return departmentRepository.findByActiveTrueOrderByNameAsc();
    }
}
