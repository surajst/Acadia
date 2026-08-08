package com.concept.messaging.data;

import com.concept.management.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Message-notification access: unread badges and mark-as-read on thread open. */
@Repository
public interface MsgNotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(UUID recipientId);

    List<Notification> findByRecipientIdAndRelatedEntityIdAndReadFalse(UUID recipientId, UUID relatedEntityId);
}
