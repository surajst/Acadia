package com.concept.notification.app;

import com.concept.notification.data.Notification;
import com.concept.notification.data.NotificationRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application layer for per-user notifications (ADR 0001). Resolves the caller
 * and reads/writes the shared {@link NotificationRepository}. Notification rows
 * are returned as {@code Object} so the interface layer keeps no static
 * dependency on the JPA entity — serialized JSON is identical.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public List<Object> list(Authentication authentication) {
        User user = requireUser(authentication);
        return List.copyOf(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()));
    }

    public Map<String, Object> unreadCount(Authentication authentication) {
        User user = requireUser(authentication);
        return Map.of("count", notificationRepository.countByRecipientIdAndReadFalse(user.getId()));
    }

    public Map<String, Object> markAsRead(UUID id, Authentication authentication) {
        User user = requireUser(authentication);
        Notification n = notificationRepository.findByIdAndTenantId(id, user.getTenantId()).orElseThrow();
        if (!n.getRecipientId().equals(user.getId())) {
            throw new IllegalArgumentException("Notification not found: " + id);
        }
        n.setRead(true);
        notificationRepository.save(n);
        return Map.of("status", "ok");
    }

    public Map<String, Object> markAllAsRead(Authentication authentication) {
        User user = requireUser(authentication);
        List<Notification> unread =
                notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return Map.of("status", "ok", "count", unread.size());
    }

    private User requireUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName()).orElseThrow();
    }
}
