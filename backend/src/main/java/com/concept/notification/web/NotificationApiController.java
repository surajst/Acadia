package com.concept.notification.web;

import com.concept.notification.app.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Interface layer for per-user notifications. Thin binding over
 * {@link NotificationService} (ADR 0001).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.list(authentication));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {
        return ResponseEntity.ok(notificationService.unreadCount(authentication));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAsRead(id, authentication));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAllAsRead(authentication));
    }
}
