package com.clinic.backend.config;

import com.clinic.backend.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes request-scoped values that every Thymeleaf view needs.
 * <p>
 * Thymeleaf 3.1 (Spring Boot 3.2) removed the {@code #httpServletRequest} expression
 * object, so templates can no longer read the current path directly. We surface it
 * here as the {@code currentUri} model attribute, which {@code layouts/base.html} uses
 * to highlight the active navigation item. We also surface the signed-in user's unread
 * in-app notification count for the nav badge.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null ? uri : "";
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        return notificationService.unreadCountForCurrentUser();
    }
}
