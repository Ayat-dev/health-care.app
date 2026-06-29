package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.ClinicConfigDto;
import com.clinic.backend.i18n.WebI18n;
import com.clinic.backend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminConfigWebController {

    private final ClinicConfigService clinicConfigService;
    private final FileStorageService fileStorageService;
    private final WebI18n i18n;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("config", clinicConfigService.toDto(clinicConfigService.getConfig()));
        return "admin/config/form";
    }

    @PostMapping
    public String save(@ModelAttribute("config") ClinicConfigDto dto,
                       @RequestParam(value = "amanataQrFile", required = false) MultipartFile amanataQrFile,
                       @RequestParam(value = "mynitaQrFile", required = false) MultipartFile mynitaQrFile,
                       Model model, RedirectAttributes ra) {
        try {
            // QR marchands : on remplace l'URL uniquement si un nouveau fichier est
            // téléversé, sinon on conserve celle déjà enregistrée (les champs ne sont
            // pas dans le formulaire texte).
            ClinicConfig current = clinicConfigService.getConfig();
            dto.setAmanataQrUrl(storeOrKeep(amanataQrFile, current.getAmanataQrUrl()));
            dto.setMynitaQrUrl(storeOrKeep(mynitaQrFile, current.getMynitaQrUrl()));

            clinicConfigService.update(dto);
            ra.addFlashAttribute("success", i18n.t("admin.config.flash.saved"));
            return "redirect:/admin/config";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/config/form";
        }
    }

    /** Stocke le fichier s'il est présent et renvoie sa nouvelle URL, sinon garde l'URL existante. */
    private String storeOrKeep(MultipartFile file, String existingUrl) {
        if (file != null && !file.isEmpty()) {
            return fileStorageService.storeImage(file, "config");
        }
        return existingUrl;
    }
}
