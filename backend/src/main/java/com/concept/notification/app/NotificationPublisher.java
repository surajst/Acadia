package com.concept.notification.app;

import com.concept.notification.data.Notification;
import com.concept.notification.data.NotificationRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Raises the notifications the app's home screen shows.
 *
 * <p>Until this existed, {@code MESSAGE} was the only type anything in
 * production ever created. ATTENDANCE, TASK, ANNOUNCEMENT and SYSTEM were
 * listed in a comment on the entity and produced solely by the dev-mode test
 * harness — so a real school's notification list only ever held unread chat
 * messages, and homework, fees and school news raised nothing at all.
 *
 * <p>Every method here is best-effort: a notification that cannot be raised
 * must never fail the thing it is about. A fee invoice is still a fee invoice
 * if the reminder does not go out, and losing the homework because the
 * notification blew up would be a far worse trade.
 */
@Service
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    /** Types the app knows how to route a tap for. Keep in step with the mobile mapping. */
    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_FEE = "FEE";
    public static final String TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";
    public static final String TYPE_ATTENDANCE = "ATTENDANCE";

    private final NotificationRepository notificationRepository;

    public NotificationPublisher(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Homework and the like, to every pupil it was set for. */
    public void taskAssigned(List<Student> students, UUID tenantId, UUID academicYearId,
                             UUID taskId, String title, String subject) {
        for (Student s : students) {
            if (s.getUserId() == null) continue;   // no login, nothing to notify
            raise(tenantId, academicYearId, s.getUserId(), "STUDENT", TYPE_TASK, taskId,
                    "New task: " + title,
                    subject == null || subject.isBlank() ? "Tap to open and hand it in"
                            : subject + " · tap to open and hand it in");
        }
    }

    /** A fee reminder, to every guardian linked to the student. */
    public void feeRaised(Student student, UUID tenantId, UUID academicYearId,
                          UUID invoiceId, String amount, String dueLabel) {
        if (student == null || student.getParents() == null) return;
        for (Parent p : student.getParents()) {
            if (p.getUserId() == null) continue;
            raise(tenantId, academicYearId, p.getUserId(), "PARENT", TYPE_FEE, invoiceId,
                    "Fees due" + (amount == null || amount.isBlank() ? "" : ": " + amount),
                    (student.getFirstName() == null ? "Your child" : student.getFirstName())
                            + (dueLabel == null || dueLabel.isBlank() ? "" : " · " + dueLabel));
        }
    }

    /** School news, to whoever it was addressed to. */
    public void announcementPosted(List<UUID> recipientUserIds, String recipientRole,
                                   UUID tenantId, UUID academicYearId,
                                   UUID announcementId, String title) {
        for (UUID userId : recipientUserIds) {
            if (userId == null) continue;
            raise(tenantId, academicYearId, userId, recipientRole, TYPE_ANNOUNCEMENT, announcementId,
                    title, "School news");
        }
    }

    /**
     * One person has dealt with the thing a notification was about.
     *
     * <p>"Waiting for you" on the home screen listed a pupil's homework until
     * somebody tapped the row, so it went on saying a task was waiting after the
     * work had been handed in -- and the bell counted it. Raising a notification
     * was only ever half the story; nothing ever retired one.
     *
     * <p>Marked read rather than deleted: they were told, and that stays true. It
     * is also what both the strip and the unread count already key off, so the
     * two cannot drift apart.
     *
     * <p>Best-effort like everything else here: failing to retire a notification
     * must not fail the hand-in it followed.
     */
    public void settledFor(UUID recipientUserId, String type, UUID relatedEntityId) {
        if (recipientUserId == null || relatedEntityId == null) {
            return;
        }
        try {
            markRead(notificationRepository
                    .findByRecipientIdAndTypeAndRelatedEntityIdAndReadFalse(
                            recipientUserId, type, relatedEntityId));
        } catch (Exception e) {
            log.warn("Could not retire the {} notification for {}", type, recipientUserId, e);
        }
    }

    /**
     * The thing itself has gone, so it is waiting for nobody.
     *
     * <p>A closed or deleted task cannot be handed in, but every pupil it was set
     * for kept an unread "New task" row pointing at it -- in the deleted case, at
     * nothing at all.
     */
    public void settledForEveryone(String type, UUID relatedEntityId, UUID tenantId) {
        if (relatedEntityId == null || tenantId == null) {
            return;
        }
        try {
            markRead(notificationRepository
                    .findByTypeAndRelatedEntityIdAndTenantIdAndReadFalse(
                            type, relatedEntityId, tenantId));
        } catch (Exception e) {
            log.warn("Could not retire the {} notifications for {}", type, relatedEntityId, e);
        }
    }

    private void markRead(List<Notification> rows) {
        if (rows.isEmpty()) {
            return;
        }
        rows.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(rows);
    }

    private void raise(UUID tenantId, UUID academicYearId, UUID recipientId, String recipientRole,
                       String type, UUID relatedEntityId, String title, String body) {
        try {
            Notification n = new Notification();
            n.setId(UUID.randomUUID());
            n.setTenantId(tenantId);
            n.setAcademicYearId(academicYearId);
            n.setRecipientId(recipientId);
            n.setRecipientRole(recipientRole);
            n.setType(type);
            n.setRelatedEntityId(relatedEntityId);
            n.setTitle(title);
            n.setBody(body);
            notificationRepository.save(n);
        } catch (Exception e) {
            // Best-effort by design -- see the class note.
            log.warn("Could not raise a {} notification for {}", type, recipientId, e);
        }
    }
}
