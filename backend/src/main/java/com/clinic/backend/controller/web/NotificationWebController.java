package com.clinic.backend.controller.web;

import com.clinic.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * In-app inbox for the signed-in user. Reads their IN_APP notifications and lets them mark
 * messages read (which clears the nav badge count surfaced by {@code GlobalModelAdvice}).
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationWebController {

    private final NotificationService notificationService;

    @GetMapping
    public String inbox(Model model) {
        model.addAttribute("notifications", notificationService.inboxForCurrentUser());
        return "notifications/list";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String markAllRead(RedirectAttributes ra) {
        notificationService.markAllRead();
        ra.addFlashAttribute("success", "Toutes les notifications ont été marquées comme lues.");
        return "redirect:/notifications";
    }
}
