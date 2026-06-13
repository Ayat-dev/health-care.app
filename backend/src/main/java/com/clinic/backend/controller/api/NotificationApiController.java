package com.clinic.backend.controller.api;

import com.clinic.backend.dto.NotificationDto;
import com.clinic.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;

    /** Filtered list (any param optional): by recipient user, status, or type. */
    @GetMapping
    public List<NotificationDto> list(@RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String type) {
        return notificationService.search(userId, status, type);
    }

    /** Queue + immediately dispatch a one-off test message (dev: logged, marked ENVOYE). */
    @PostMapping("/test-sms")
    @PreAuthorize("hasRole('ADMIN')")
    public NotificationDto testSms(@RequestBody Map<String, String> body) {
        return notificationService.sendTest(
                body.getOrDefault("channel", "SMS"),
                body.get("recipient"),
                body.get("body"));
    }
}
