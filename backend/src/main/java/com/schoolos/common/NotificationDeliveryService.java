package com.schoolos.common;

/**
 * Seam for dispatching an external notification (SMS/WhatsApp/etc.) to a
 * recipient. The default implementation just logs to console; swapping in a
 * real provider (Twilio, WhatsApp Business API) only requires a new bean.
 */
public interface NotificationDeliveryService {
    void send(String recipient, String message);
}
