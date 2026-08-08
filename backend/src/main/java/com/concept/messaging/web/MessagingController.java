package com.concept.messaging.web;

import com.concept.messaging.app.MessagingException;
import com.concept.messaging.app.MessagingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for messaging (ADR 0001). Binds requests, enforces the role
 * gate, and shapes JSON responses. All decisions, access control and storage
 * live in {@link MessagingService}; no entities or repositories appear here.
 */
@RestController
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    // ─── Conversations ─────────────────────────────────────────────────────

    @GetMapping("/api/messages/conversations")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> listConversations(Authentication authentication) {
        return ResponseEntity.ok(messagingService.listConversations(authentication.getName()));
    }

    @PostMapping("/api/messages/conversations/start")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> startConversation(@RequestBody StartConversationRequest request, Authentication authentication) {
        return ResponseEntity.ok(messagingService.startConversation(
                authentication.getName(), request.studentId(), request.teacherId(), request.body()));
    }

    @GetMapping("/api/messages/conversations/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> getThread(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(messagingService.getThread(authentication.getName(), id));
    }

    @PostMapping("/api/messages/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> reply(@PathVariable UUID id, @RequestBody ReplyRequest request, Authentication authentication) {
        return ResponseEntity.ok(messagingService.reply(authentication.getName(), id, request.body()));
    }

    @PostMapping("/api/messages/conversations/{id}/voice-reply")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> voiceReply(@PathVariable UUID id, @RequestParam("audio") MultipartFile audio,
                                        @RequestParam String lang, Authentication authentication) {
        byte[] audioBytes;
        try {
            audioBytes = audio.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not read uploaded audio"));
        }
        return ResponseEntity.ok(messagingService.voiceReply(authentication.getName(), id, audioBytes, lang));
    }

    @GetMapping("/api/messages/conversations/{id}/messages/{messageId}/localized")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> getMessageLocalized(@PathVariable UUID id, @PathVariable UUID messageId,
                                                 @RequestParam String lang, Authentication authentication) {
        return ResponseEntity.ok(Map.of("body", messagingService.localizedMessage(authentication.getName(), id, messageId, lang)));
    }

    @GetMapping("/api/messages/conversations/{id}/messages/{messageId}/speech")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> getMessageSpeech(@PathVariable UUID id, @PathVariable UUID messageId,
                                              @RequestParam String lang, Authentication authentication) {
        var speech = messagingService.messageSpeech(authentication.getName(), id, messageId, lang);
        return ResponseEntity.ok(Map.of("audioBase64", speech.audioBase64(), "contentType", speech.contentType()));
    }

    // ─── Rosters ───────────────────────────────────────────────────────────

    @GetMapping("/api/teacher/messages/roster")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> teacherRoster(Authentication authentication) {
        return ResponseEntity.ok(messagingService.teacherRoster(authentication.getName()));
    }

    @GetMapping("/api/admin/messages/roster")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminRoster(Authentication authentication) {
        return ResponseEntity.ok(messagingService.adminRoster(authentication.getName()));
    }

    @GetMapping("/api/parent/messages/teachers")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> parentTeachers(@RequestParam UUID studentId, Authentication authentication) {
        return ResponseEntity.ok(messagingService.parentTeachers(authentication.getName(), studentId));
    }

    /** Translate a messaging failure into its intended HTTP status + error body. */
    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<?> handle(MessagingException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    public record StartConversationRequest(UUID studentId, UUID teacherId, String body) {}
    public record ReplyRequest(String body) {}
}
