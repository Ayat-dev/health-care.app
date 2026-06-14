package com.clinic.backend.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Gère les erreurs HTTP côté web Thymeleaf :
 * 403 Forbidden, 404 Not Found, 500 Internal Server Error.
 */
@Controller
public class ErrorWebController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Integer status = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        String message = (String) request.getAttribute("jakarta.servlet.error.message");

        if (status != null) {
            model.addAttribute("status", status);
            if (status == HttpStatus.FORBIDDEN.value()) {
                model.addAttribute("title", "Accès refusé");
                model.addAttribute("message", "Vous n'avez pas les droits nécessaires pour accéder à cette page.");
            } else if (status == HttpStatus.NOT_FOUND.value()) {
                model.addAttribute("title", "Page introuvable");
                model.addAttribute("message", "La page que vous cherchez n'existe pas ou a été déplacée.");
            } else {
                model.addAttribute("title", "Erreur");
                model.addAttribute("message", message != null ? message : "Une erreur inattendue s'est produite.");
            }
        }

        return "error";
    }
}
