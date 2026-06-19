package com.clinic.backend.controller.web;

import com.clinic.backend.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Consultation du journal d'audit (lecture seule, ADMIN). Filtres optionnels
 * sur l'utilisateur, le type d'entité, l'action et une plage de dates.
 */
@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAuditWebController {

    private static final int MAX_ROWS = 300;

    private final AuditService auditService;

    @GetMapping
    public String list(@RequestParam(required = false) String username,
                       @RequestParam(required = false) String entityType,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        LocalDateTime fromTs = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime toTs   = (to != null) ? to.atTime(LocalTime.MAX) : null;

        model.addAttribute("entries",
                auditService.search(username, entityType, action, fromTs, toTs, MAX_ROWS));
        model.addAttribute("entityTypes", auditService.entityTypes());
        model.addAttribute("actions", auditService.actions());
        model.addAttribute("username", username);
        model.addAttribute("entityType", entityType);
        model.addAttribute("action", action);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("maxRows", MAX_ROWS);
        return "admin/audit/list";
    }
}
