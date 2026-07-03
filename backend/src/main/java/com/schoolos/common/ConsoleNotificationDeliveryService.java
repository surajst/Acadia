package com.schoolos.common;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Default NotificationDeliveryService — prints the already-formatted message
 * to console, matching this project's pre-existing dev-mode stub behavior.
 */
@Service
@Primary
public class ConsoleNotificationDeliveryService implements NotificationDeliveryService {
    @Override
    public void send(String recipient, String message) {
        System.out.println(message);
    }
}
