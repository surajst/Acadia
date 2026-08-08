package com.concept.messaging.app;

import java.time.LocalDateTime;
import java.util.UUID;

/** Flat, entity-free views returned to the messaging web layer (ADR 0001). */
public final class MessagingViews {
    private MessagingViews() {}

    /** One row in a user's conversation list. */
    public record ConversationSummary(UUID id, UUID studentId, String studentName,
                                      UUID teacherId, String teacherName,
                                      String lastMessagePreview, LocalDateTime lastMessageAt, boolean unread) {}

    /** A conversation identity returned when starting/opening a thread. */
    public record ConversationRef(UUID id, UUID studentId, String studentName,
                                  UUID teacherId, String teacherName) {}

    /** One message inside a thread, from the caller's perspective. */
    public record MessageView(UUID id, UUID senderId, String senderRole, boolean mine,
                              String body, LocalDateTime createdAt) {}

    /** A freshly posted message (reply / voice-reply response). */
    public record MessageRef(UUID id, String body, LocalDateTime createdAt) {}

    /** A student a teacher/admin can message about. */
    public record RosterEntry(UUID studentId, String studentName, String className, boolean hasParent) {}

    /** A teacher a parent can start a conversation with. */
    public record TeacherOption(UUID teacherId, String teacherName, String subjectName) {}

    /** Synthesized speech for a localized message. */
    public record SpeechResult(String audioBase64, String contentType) {}
}
