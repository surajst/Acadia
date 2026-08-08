package com.concept.messaging.app;

import com.concept.language.SpeechService;
import com.concept.language.SupportedLanguages;
import com.concept.language.TranslationService;
import com.concept.management.ClassSection;
import com.concept.management.Conversation;
import com.concept.management.Message;
import com.concept.management.Notification;
import com.concept.management.Parent;
import com.concept.management.Student;
import com.concept.management.SubjectAssignment;
import com.concept.messaging.app.MessagingViews.*;
import com.concept.messaging.data.*;
import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for messaging (ADR 0001). Owns conversation access control,
 * message posting, notifications, and localization — the web layer only binds
 * requests and shapes JSON.
 *
 * <p>Every conversation, student, teacher and parent is resolved through a
 * {@code findByIdAndTenantId} on a {@link com.concept.common.TenantScopedRepository},
 * scoped to the acting user's own tenant. A thread from another school is simply
 * not found, so the previous hand-written ownership checks are now backed by
 * structural tenant isolation.
 */
@Service
public class MessagingService {

    private final MsgConversationRepository conversationRepository;
    private final MsgMessageRepository messageRepository;
    private final MsgNotificationRepository notificationRepository;
    private final MsgStudentRepository studentRepository;
    private final MsgUserRepository userRepository;
    private final MsgParentRepository parentRepository;
    private final MsgSubjectAssignmentRepository subjectAssignmentRepository;
    private final TranslationService translationService;
    private final SpeechService speechService;

    public MessagingService(MsgConversationRepository conversationRepository,
                            MsgMessageRepository messageRepository,
                            MsgNotificationRepository notificationRepository,
                            MsgStudentRepository studentRepository,
                            MsgUserRepository userRepository,
                            MsgParentRepository parentRepository,
                            MsgSubjectAssignmentRepository subjectAssignmentRepository,
                            TranslationService translationService,
                            SpeechService speechService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.parentRepository = parentRepository;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.translationService = translationService;
        this.speechService = speechService;
    }

    // ─── Conversations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(String email) {
        User me = me(email);
        List<Conversation> conversations;
        if (isStaff(me)) {
            conversations = conversationRepository.findByTeacherIdOrderByLastMessageAtDesc(me.getId());
        } else {
            Parent parent = parentRepository.findByUserId(me.getId()).orElse(null);
            if (parent == null) return List.of();
            List<UUID> studentIds = studentRepository.findByParentsContaining(parent).stream()
                    .map(Student::getId).collect(Collectors.toList());
            conversations = studentIds.isEmpty() ? List.of()
                    : conversationRepository.findByStudentIdInOrderByLastMessageAtDesc(studentIds);
        }

        Set<UUID> unread = notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(me.getId()).stream()
                .filter(n -> "MESSAGE".equals(n.getType()) && n.getRelatedEntityId() != null)
                .map(Notification::getRelatedEntityId)
                .collect(Collectors.toSet());

        UUID tenantId = me.getTenantId();
        return conversations.stream().map(c -> {
            Student student = studentRepository.findByIdAndTenantId(c.getStudentId(), tenantId).orElse(null);
            User teacher = userRepository.findByIdAndTenantId(c.getTeacherId(), tenantId).orElse(null);
            Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());
            return new ConversationSummary(c.getId(), c.getStudentId(), fullName(student),
                    c.getTeacherId(), teacher != null ? teacher.getFullName() : "",
                    last != null ? last.getBody() : "", c.getLastMessageAt(), unread.contains(c.getId()));
        }).collect(Collectors.toList());
    }

    @Transactional
    public ConversationRef startConversation(String email, UUID studentId, UUID teacherId, String body) {
        User me = me(email);
        UUID tenantId = me.getTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId).orElse(null);
        if (student == null) throw MessagingException.badRequest("Student not found");

        if (me.getRole() == UserRole.ADMIN) {
            // Admins oversee the whole school — the tenant scope above is the only check.
            Conversation adminConv = conversationRepository.findByStudentIdAndTeacherId(student.getId(), me.getId())
                    .orElseGet(() -> newConversation(student, me.getId()));
            postMessage(adminConv, me, student, body);
            return ref(adminConv, student, me);
        }

        UUID resolvedTeacherId = me.getRole() == UserRole.TEACHER ? me.getId() : teacherId;
        User teacher = resolvedTeacherId != null ? userRepository.findByIdAndTenantId(resolvedTeacherId, tenantId).orElse(null) : null;
        if (teacher == null || teacher.getRole() != UserRole.TEACHER) {
            throw MessagingException.badRequest("Teacher not found");
        }
        if (!subjectAssignmentRepository.existsByTeacherAndClassSection(teacher, student.getClassSection())) {
            throw MessagingException.badRequest("Teacher is not assigned to this student's class");
        }
        if (me.getRole() == UserRole.PARENT && !isGuardianOf(me, student)) {
            throw MessagingException.forbidden("Not authorized for this student");
        }

        Conversation conversation = conversationRepository.findByStudentIdAndTeacherId(student.getId(), teacher.getId())
                .orElseGet(() -> newConversation(student, teacher.getId()));
        postMessage(conversation, me, student, body);
        return ref(conversation, student, teacher);
    }

    @Transactional
    public List<MessageView> getThread(String email, UUID conversationId) {
        User me = me(email);
        Conversation conversation = accessibleConversation(conversationId, me);

        List<MessageView> rows = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> new MessageView(m.getId(), m.getSenderId(), m.getSenderRole(),
                        m.getSenderId().equals(me.getId()), m.getBody(), m.getCreatedAt()))
                .collect(Collectors.toList());

        List<Notification> unread = notificationRepository
                .findByRecipientIdAndRelatedEntityIdAndReadFalse(me.getId(), conversationId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return rows;
    }

    @Transactional
    public MessageRef reply(String email, UUID conversationId, String body) {
        User me = me(email);
        Conversation conversation = accessibleConversation(conversationId, me);
        Student student = studentRepository.findByIdAndTenantId(conversation.getStudentId(), me.getTenantId()).orElse(null);
        Message message = postMessage(conversation, me, student, body);
        return new MessageRef(message.getId(), message.getBody(), message.getCreatedAt());
    }

    @Transactional
    public MessageRef voiceReply(String email, UUID conversationId, byte[] audioBytes, String lang) {
        if (!SupportedLanguages.isSupported(lang)) throw MessagingException.badRequest("Unsupported language");
        User me = me(email);
        Conversation conversation = accessibleConversation(conversationId, me);

        String transcript = speechService.transcribe(audioBytes, lang);
        String body = "en".equals(lang) ? transcript : translationService.translate(transcript, "en");

        Student student = studentRepository.findByIdAndTenantId(conversation.getStudentId(), me.getTenantId()).orElse(null);
        Message message = postMessage(conversation, me, student, body);
        return new MessageRef(message.getId(), message.getBody(), message.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public String localizedMessage(String email, UUID conversationId, UUID messageId, String lang) {
        Message message = ownMessage(email, conversationId, messageId);
        if (!SupportedLanguages.isSupported(lang)) throw MessagingException.badRequest("Unsupported language");
        return translationService.translate(message.getBody(), lang);
    }

    @Transactional(readOnly = true)
    public SpeechResult messageSpeech(String email, UUID conversationId, UUID messageId, String lang) {
        Message message = ownMessage(email, conversationId, messageId);
        if (!SupportedLanguages.isSupported(lang)) throw MessagingException.badRequest("Unsupported language");
        String localized = translationService.translate(message.getBody(), lang);
        byte[] audio = speechService.synthesizeSpeech(localized, lang);
        return new SpeechResult(Base64.getEncoder().encodeToString(audio), "audio/mpeg");
    }

    // ─── Rosters ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RosterEntry> teacherRoster(String email) {
        User teacher = me(email);
        List<ClassSection> sections = subjectAssignmentRepository.findByTeacher(teacher).stream()
                .map(SubjectAssignment::getClassSection).distinct().collect(Collectors.toList());
        List<Student> students = sections.isEmpty() ? List.of() : studentRepository.findByClassSectionIn(sections);
        return students.stream().map(this::rosterEntry).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RosterEntry> adminRoster(String email) {
        User me = me(email);
        List<Student> students = me.getTenantId() != null ? studentRepository.findByTenantId(me.getTenantId()) : List.of();
        return students.stream().map(this::rosterEntry).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeacherOption> parentTeachers(String email, UUID studentId) {
        User me = me(email);
        Student student = studentRepository.findByIdAndTenantId(studentId, me.getTenantId()).orElse(null);
        if (student == null || !isGuardianOf(me, student)) {
            throw MessagingException.forbidden("Not authorized for this student");
        }
        return subjectAssignmentRepository.findByClassSection(student.getClassSection()).stream()
                .map(a -> new TeacherOption(a.getTeacher().getId(), a.getTeacher().getFullName(), a.getSubjectName()))
                .collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private User me(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> MessagingException.forbidden("User not found"));
    }

    private boolean isStaff(User u) {
        return u.getRole() == UserRole.TEACHER || u.getRole() == UserRole.ADMIN;
    }

    private boolean isGuardianOf(User me, Student student) {
        Parent parent = parentRepository.findByUserId(me.getId()).orElse(null);
        return parent != null && student.getParents().contains(parent);
    }

    /** Resolve a conversation the caller may access, tenant-scoped; throws otherwise. */
    private Conversation accessibleConversation(UUID conversationId, User me) {
        Conversation conversation = conversationRepository.findByIdAndTenantId(conversationId, me.getTenantId()).orElse(null);
        if (conversation == null) throw MessagingException.badRequest("Conversation not found");
        boolean ok = isStaff(me)
                ? conversation.getTeacherId().equals(me.getId())
                : parentCanAccess(conversation, me);
        if (!ok) throw MessagingException.forbidden("Not authorized for this conversation");
        return conversation;
    }

    private boolean parentCanAccess(Conversation conversation, User me) {
        Student student = studentRepository.findByIdAndTenantId(conversation.getStudentId(), me.getTenantId()).orElse(null);
        return student != null && isGuardianOf(me, student);
    }

    private Message ownMessage(String email, UUID conversationId, UUID messageId) {
        User me = me(email);
        accessibleConversation(conversationId, me); // throws if not accessible
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null || !message.getConversationId().equals(conversationId)) {
            throw MessagingException.badRequest("Message not found");
        }
        return message;
    }

    private Conversation newConversation(Student student, UUID teacherId) {
        Conversation c = new Conversation();
        c.setId(UUID.randomUUID());
        c.setTenantId(student.getTenantId());
        c.setAcademicYearId(student.getAcademicYearId());
        c.setStudentId(student.getId());
        c.setTeacherId(teacherId);
        return conversationRepository.save(c);
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
        if (isStaff(sender)) {
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

    private RosterEntry rosterEntry(Student s) {
        ClassSection cs = s.getClassSection();
        String className = cs != null ? cs.getGradeName() + " – " + cs.getSectionName() : "";
        return new RosterEntry(s.getId(), s.getFirstName() + " " + s.getLastName(), className, !s.getParents().isEmpty());
    }

    private ConversationRef ref(Conversation c, Student student, User teacher) {
        return new ConversationRef(c.getId(), c.getStudentId(), fullName(student),
                c.getTeacherId(), teacher != null ? teacher.getFullName() : "");
    }

    private static String fullName(Student s) {
        return s != null ? s.getFirstName() + " " + s.getLastName() : "";
    }
}
