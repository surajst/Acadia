package com.concept.notification.data;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends TenantScopedRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(UUID recipientId);
    long countByRecipientIdAndReadFalse(UUID recipientId);
    List<Notification> findByRecipientIdAndRelatedEntityIdAndReadFalse(UUID recipientId, UUID relatedEntityId);

    /**
     * Every unread notification of one kind about one thing, across recipients.
     *
     * <p>For retiring a notification once what it is about stops being
     * actionable -- a task that has been closed or deleted is no longer waiting
     * for anybody. Tenant-scoped even though the entity id is a UUID the caller
     * has already resolved within its own school: the cheap check is the one
     * that survives a caller that forgets.
     */
    List<Notification> findByTypeAndRelatedEntityIdAndTenantIdAndReadFalse(
            String type, UUID relatedEntityId, UUID tenantId);

    /** The same, for one recipient: they have dealt with it, nobody else has. */
    List<Notification> findByRecipientIdAndTypeAndRelatedEntityIdAndReadFalse(
            UUID recipientId, String type, UUID relatedEntityId);
}