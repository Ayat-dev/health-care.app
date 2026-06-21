package com.clinic.backend.controller.web;

import com.clinic.backend.dto.SearchResultDto;
import com.clinic.backend.search.GlobalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Recherche globale (P3.5) — chaîne web (session).
 * <ul>
 *   <li>{@code GET /search/suggest?q=} → JSON pour la palette de commandes (fetch live)</li>
 *   <li>{@code GET /search?q=}         → page de résultats (repli sans-JS / Entrée)</li>
 * </ul>
 * Servi en session comme l'auto-complétion CIM-10 ({@code /consultations/icd10/search}),
 * la chaîne {@code /api/**} étant réservée au JWT.
 */
@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class GlobalSearchWebController {

    private final GlobalSearchService searchService;

    @GetMapping("/suggest")
    @ResponseBody
    public List<SearchResultDto> suggest(@RequestParam(value = "q", required = false) String q) {
        return searchService.search(q, currentRole());
    }

    @GetMapping
    public String page(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("results", searchService.search(q, currentRole()));
        return "search/results";
    }

    /** Rôle de l'utilisateur courant (ex. "SECRETAIRE"), null si non authentifié. */
    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) return null;
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse(null);
    }
}
