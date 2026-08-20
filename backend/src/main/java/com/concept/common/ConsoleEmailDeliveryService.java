package com.concept.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Default EmailDeliveryService: logs the message and reports that nothing was
 * actually delivered.
 *
 * <p>Reporting notConfigured() rather than delivered() is the point. If this
 * claimed success, an admin would be told a teacher had been emailed their
 * credentials when no email exists -- the teacher never hears, the admin never
 * follows up, and nobody finds out until the teacher cannot log in.
 */
@Service
@Primary
public class ConsoleEmailDeliveryService implements EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailDeliveryService.class);

    @Override
    public EmailResult send(String toAddress, String subject, String body) {
        log.info("[email not configured] to={} subject={}\n{}", toAddress, subject, body);
        return EmailResult.notConfigured();
    }
}
