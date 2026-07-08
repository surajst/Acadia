package com.schoolos.management;

import com.schoolos.language.SpeechService;
import com.schoolos.language.SupportedLanguages;
import com.schoolos.language.TranslationService;
import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class MessagingApiController {

    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private TranslationService translationService;
    @Autowired private SpeechService speechService;

    // ─── Conversations ────────────────────────────────────────────────────────

    @GetMapping("/api/messages/conversations")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> listConversations(Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();

        List<Conversation> conversations;
        if (me.getRole() == UserRole.TEACHER || me.getRole() == UserRole.ADMIN) {
            conversations = conversationRepository.findByTeacherIdOrderByLastMessageAtDesc(me.getId());
        } else {
            Parent parent = parentRepository.findByUserId(me.getId()).orElse(null);
            if (parent == null) return ResponseEntity.ok(List.of());
            List<UUID> studentIds = studentRepository.findByParentsContaining(parent).stream()
                    .map(Student::getId).collect(Collectors.toList());
            conversations = studentIds.isEmpty() ? List.of()
                    : conversationRepository.findByStudentIdInOrderByLastMessageAtDesc(studentIds);
        }

        Set<UUID> unreadConversationIds = notificationRepository
                .findByRecipientIdAndReadFalseOrderByCreatedAtDesc(me.getId()).stream()
                .filter(n -> "MESSAGE".equals(n.getType()) && n.getRelatedEntityId() != null)
                .map(Notification::getRelatedEntityId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> result = conversations.stream().map(c -> {
            Student student = studentRepository.findById(c.getStudentId()).orElse(null);
            User teacher = userRepository.findById(c.getTeacherId()).orElse(null);
            Message lastMessage = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("studentId", c.getStudentId());
            row.put("studentName", student != null ? student.getFirstName() + " " + student.getLastName() : "");
            row.put("teacherId", c.getTeacherId());
            row.put("teacherName", teacher != null ? teacher.getFullName() : "");
            row.put("lastMessagePreview", lastMessage != null ? lastMessage.getBody() : "");
            row.put("lastMessageAt", c.getLastMessageAt());
            row.put("unread", unreadConversationIds.contains(c.getId()));
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/messages/conversations/start")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> startConversation(@RequestBody StartConversationRequest request, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();

        Student student = studentRepository.findById(request.getStudentId()).orElse(null);
        if (student == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }

        if (me.getRole() == UserRole.ADMIN) {
            // Admins aren't tied to a SubjectAssignment — they oversee the
            // whole school, so the only scoping check that applies is tenant.
            UUID currentTenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            if (currentTenantId == null || !currentTenantId.equals(student.getTenantId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
            }
            Conversation adminConversation = conversationRepository.findByStudentIdAndTeacherId(student.getId(), me.getId())
                    .orElseGet(() -> {
                        Conversation c = new Conversation();
                        c.setId(UUID.randomUUID());
                        c.setTenantId(student.getTenantId());
                        c.setAcademicYearId(student.getAcademicYearId());
                        c.setStudentId(student.getId());
                        c.setTeacherId(me.getId());
                        return conversationRepository.save(c);
                    });
            postMessage(adminConversation, me, student, request.getBody());
            return ResponseEntity.ok(toConversationMap(adminConversation, student, me));
        }

        UUID teacherId = request.getTeacherId();
        if (me.getRole() == UserRole.TEACHER) {
            teacherId = me.getId();
        }
        User teacher = teacherId != null ? userRepository.findById(teacherId).orElse(null) : null;
        if (teacher == null || teacher.getRole() != UserRole.TEACHER) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found"));
        }
        if (!subjectAssignmentRepository.existsByTeacherAndClassSection(teacher, student.getClassSection())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher is not assigned to this student's class"));
        }

        if (me.getRole() == UserRole.PARENT) {
            Parent parent = parentRepository.findByUserId(me.getId()).orElse(null);
            if (parent == null || !student.getParents().contains(parent)) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this student"));
            }
        }

        Conversation conversation = conversationRepository.findByStudentIdAndTeacherId(student.getId(), teacher.getId())
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setId(UUID.randomUUID());
                    c.setTenantId(student.getTenantId());
                    c.setAcademicYearId(student.getAcademicYearId());
                    c.setStudentId(student.getId());
                    c.setTeacherId(teacher.getId());
                    return conversationRepository.save(c);
                });

        postMessage(conversation, me, student, request.getBody());
        return ResponseEntity.ok(toConversationMap(conversation, student, teacher));
    }

    @GetMapping("/api/messages/conversations/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> getThread(@PathVariable UUID id, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
        }
        if (!canAccess(conversation, me)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this conversation"));
        }

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        List<Map<String, Object>> messageRows = messages.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("senderId", m.getSenderId());
            row.put("senderRole", m.getSenderRole());
            row.put("mine", m.getSenderId().equals(me.getId()));
            row.put("body", m.getBody());
            row.put("createdAt", m.getCreatedAt());
            return row;
        }).collect(Collectors.toList());

        List<Notification> unread = notificationRepository
                .findByRecipientIdAndRelatedEntityIdAndReadFalse(me.getId(), id);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);

        return ResponseEntity.ok(messageRows);
    }

    @PostMapping("/api/messages/conversations/{id}/messages")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> reply(@PathVariable UUID id, @RequestBody ReplyRequest request, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
        }
        if (!canAccess(conversation, me)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this conversation"));
        }

        Student student = studentRepository.findById(conversation.getStudentId()).orElse(null);
        Message message = postMessage(conversation, me, student, request.getBody());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.getId());
        row.put("body", message.getBody());
        row.put("createdAt", message.getCreatedAt());
        return ResponseEntity.ok(row);
    }

    @PostMapping("/api/messages/conversations/{id}/voice-reply")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> voiceReply(@PathVariable UUID id, @RequestParam("audio") MultipartFile audio,
                                         @RequestParam String lang, Authentication authentication) {
        if (!SupportedLanguages.isSupported(lang)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language"));
        }
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Conversation conversation = conversationRepository.findById(id).orElse(null);
        if (conversation == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Conversation not found"));
        }
        if (!canAccess(conversation, me)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this conversation"));
        }

        String transcript;
        try {
            transcript = speechService.transcribe(audio.getBytes(), lang);
        } catch (IOException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Could not read uploaded audio"));
        }
        String body = "en".equals(lang) ? transcript : translationService.translate(transcript, "en");

        Student student = studentRepository.findById(conversation.getStudentId()).orElse(null);
        Message message = postMessage(conversation, me, student, body);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.getId());
        row.put("body", message.getBody());
        row.put("createdAt", message.getCreatedAt());
        return ResponseEntity.ok(row);
    }

    @GetMapping("/api/messages/conversations/{id}/messages/{messageId}/localized")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> getMessageLocalized(@PathVariable UUID id, @PathVariable UUID messageId,
                                                  @RequestParam String lang, Authentication authentication) {
        Message message = resolveOwnMessage(id, messageId, authentication);
        if (message == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message not found"));
        }
        if (!SupportedLanguages.isSupported(lang)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language"));
        }
        return ResponseEntity.ok(Map.of("body", translationService.translate(message.getBody(), lang)));
    }

    @GetMapping("/api/messages/conversations/{id}/messages/{messageId}/speech")
    @PreAuthorize("hasAnyRole('TEACHER', 'PARENT', 'ADMIN')")
    public ResponseEntity<?> getMessageSpeech(@PathVariable UUID id, @PathVariable UUID messageId,
                                               @RequestParam String lang, Authentication authentication) {
        Message message = resolveOwnMessage(id, messageId, authentication);
        if (message == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message not found"));
        }
        if (!SupportedLanguages.isSupported(lang)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported language"));
        }
        String localizedText = translationService.translate(message.getBody(), lang);
        byte[] audio = speechService.synthesizeSpeech(localizedText, lang);
        return ResponseEntity.ok(Map.of(
                "audioBase64", Base64.getEncoder().encodeToString(audio),
                "contentType", "audio/mpeg"
        ));
    }

    /** A message is only reachable through a conversation the requester can already access. */
    private Message resolveOwnMessage(UUID conversationId, UUID messageId, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || !canAccess(conversation, me)) return null;
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null || !message.getConversationId().equals(conversationId)) return null;
        return message;
    }

    // ─── Rosters ──────────────────────────────────────────────────────────────

    @GetMapping("/api/teacher/messages/roster")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> teacherRoster(Authentication authentication) {
        User teacher = userRepository.findByEmail(authentication.getName()).orElseThrow();
        List<SubjectAssignment> assignments = subjectAssignmentRepository.findByTeacher(teacher);
        List<ClassSection> sections = assignments.stream().map(SubjectAssignment::getClassSection)
                .distinct().collect(Collectors.toList());
        List<Student> students = sections.isEmpty() ? List.of() : studentRepository.findByClassSectionIn(sections);

        List<Map<String, Object>> result = students.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.getId());
            row.put("studentName", s.getFirstName() + " " + s.getLastName());
            row.put("className", s.getClassSection().getGradeName() + " – " + s.getClassSection().getSectionName());
            row.put("hasParent", !s.getParents().isEmpty());
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/admin/messages/roster")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminRoster(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<Student> students = tenantId != null ? studentRepository.findByTenantId(tenantId) : List.of();

        List<Map<String, Object>> result = students.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", s.getId());
            row.put("studentName", s.getFirstName() + " " + s.getLastName());
            row.put("className", s.getClassSection() != null
                    ? s.getClassSection().getGradeName() + " – " + s.getClassSection().getSectionName()
                    : "");
            row.put("hasParent", !s.getParents().isEmpty());
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/parent/messages/teachers")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> parentTeachers(@RequestParam UUID studentId, Authentication authentication) {
        User me = userRepository.findByEmail(authentication.getName()).orElseThrow();
        Parent parent = parentRepository.findByUserId(me.getId()).orElse(null);
        Student student = studentRepository.findById(studentId).orElse(null);
        if (parent == null || student == null || !student.getParents().contains(parent)) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this student"));
        }

        List<SubjectAssignment> assignments = subjectAssignmentRepository.findByClassSection(student.getClassSection());
        List<Map<String, Object>> result = assignments.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teacherId", a.getTeacher().getId());
            row.put("teacherName", a.getTeacher().getFullName());
            row.put("subjectName", a.getSubjectName());
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean canAccess(Conversation conversation, User me) {
        if (me.getRole() == UserRole.TEACHER || me.getRole() == UserRole.ADMIN) {
            return conversation.getTeacherId().equals(me.getId());
        }
        if (me.getRole() == UserRole.PARENT) {
            Parent parent = parentRepository.findByUserId(me.getId()).orElse(null);
            if (parent == null) return false;
            Student student = studentRepository.findById(conversation.getStudentId()).orElse(null);
            return student != null && student.getParents().contains(parent);
        }
        return false;
    }

    private Message postMessage(Conversation conversation, User sender, Student student, String body) {
        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversation.getId());
        message.setSenderId(sender.getId());
        message.setSenderRole(sender.getRole().name());
        message.setBody(body);
        messageRepository.save(message);

        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        notifyOtherParticipants(conversation, sender, student, body);
        return message;
    }

    private void notifyOtherParticipants(Conversation conversation, User sender, Student student, String body) {
        String preview = body.length() > 100 ? body.substring(0, 100) + "…" : body;

        if (sender.getRole() == UserRole.TEACHER || sender.getRole() == UserRole.ADMIN) {
            if (student == null) return;
            for (Parent parent : student.getParents()) {
                if (parent.getUserId() == null) continue;
                createNotification(conversation, parent.getUserId(), "PARENT",
                        "New message from " + sender.getFullName(), preview);
            }
        } else {
            createNotification(conversation, conversation.getTeacherId(), "TEACHER",
                    "New message from " + sender.getFullName(), preview);
        }
    }

    private void createNotification(Conversation conversation, UUID recipientId, String recipientRole, String title, String body) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setTenantId(conversation.getTenantId());
        n.setAcademicYearId(conversation.getAcademicYearId());
        n.setRecipientId(recipientId);
        n.setRecipientRole(recipientRole);
        n.setTitle(title);
        n.setBody(body);
        n.setType("MESSAGE");
        n.setRelatedEntityId(conversation.getId());
        notificationRepository.save(n);
    }

    private Map<String, Object> toConversationMap(Conversation c, Student student, User teacher) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("studentId", c.getStudentId());
        row.put("studentName", student != null ? student.getFirstName() + " " + student.getLastName() : "");
        row.put("teacherId", c.getTeacherId());
        row.put("teacherName", teacher != null ? teacher.getFullName() : "");
        return row;
    }

    public static class StartConversationRequest {
        private UUID studentId;
        private UUID teacherId;
        private String body;

        public UUID getStudentId() { return studentId; }
        public void setStudentId(UUID studentId) { this.studentId = studentId; }
        public UUID getTeacherId() { return teacherId; }
        public void setTeacherId(UUID teacherId) { this.teacherId = teacherId; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    public static class ReplyRequest {
        private String body;
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }
}
