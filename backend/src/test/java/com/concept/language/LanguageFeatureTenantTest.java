package com.concept.language;

import com.concept.announcement.Announcement;
import com.concept.announcement.AnnouncementRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.messaging.data.Conversation;
import com.concept.messaging.data.ConversationRepository;
import com.concept.parent.app.ParentException;
import com.concept.parent.app.ParentService;
import com.concept.messaging.app.MessagingException;
import com.concept.messaging.app.MessagingService;
import com.concept.messaging.app.MessagingViews.MessageRef;
import com.concept.shared.data.Parent;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Covers tenant/ownership boundaries for the Azure translation/speech
// features: a parent can only translate/hear announcements from their own
// tenant, and voice-reply/localized/speech on messages only work for a
// conversation the requester can already access. TranslationService and
// SpeechService are mocked so these tests never hit real Azure endpoints.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class LanguageFeatureTenantTest {

    @Autowired
    private ParentService parentService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @MockBean
    private TranslationService translationService;

    @MockBean
    private SpeechService speechService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID academicYearIdA;
    private UUID academicYearIdB;
    private Announcement announcementA;
    private Parent parentA;
    private User parentUserA;
    private Authentication asParentA;

    private Tenant makeTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test Tenant " + tenant.getId());
        tenant.setSubdomain("test-" + tenant.getId());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        return tenantRepository.saveAndFlush(tenant);
    }

    private UUID makeAcademicYear(UUID tenantId) {
        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        return academicYearRepository.saveAndFlush(year).getId();
    }

    private User makeUser(UUID tenantId, UUID academicYearId, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenantId);
        user.setAcademicYearId(academicYearId);
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setFullName(role.name() + " User");
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Authentication authFor(User user) {
        return new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private Authentication actAs(Authentication auth) {
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    @BeforeEach
    public void setup() {
        tenantA = makeTenant().getId();
        tenantB = makeTenant().getId();
        academicYearIdA = makeAcademicYear(tenantA);
        academicYearIdB = makeAcademicYear(tenantB);

        User adminUserA = makeUser(tenantA, academicYearIdA, UserRole.ADMIN);

        announcementA = new Announcement();
        announcementA.setId(UUID.randomUUID());
        announcementA.setTenantId(tenantA);
        announcementA.setAcademicYearId(academicYearIdA);
        announcementA.setTitle("Sports Day");
        announcementA.setContent("School closes early on Friday.");
        announcementA.setTargetGrade("ALL");
        announcementA.setCreatedBy(adminUserA.getId());
        announcementA.setCreatedAt(LocalDateTime.now());
        announcementRepository.saveAndFlush(announcementA);

        parentUserA = makeUser(tenantA, academicYearIdA, UserRole.PARENT);
        parentA = new Parent();
        parentA.setId(UUID.randomUUID());
        parentA.setTenantId(tenantA);
        parentA.setAcademicYearId(academicYearIdA);
        parentA.setFirstName("Parent");
        parentA.setLastName("A");
        parentA.setUserId(parentUserA.getId());
        parentRepository.saveAndFlush(parentA);

        asParentA = authFor(parentUserA);

        when(translationService.translate(anyString(), anyString())).thenReturn("<translated>");
        when(speechService.synthesizeSpeech(anyString(), anyString())).thenReturn(new byte[] { 1, 2, 3 });
        when(speechService.transcribe(any(byte[].class), anyString())).thenReturn("hola profesor");
    }

    @Test
    public void parentCanTranslateOwnTenantAnnouncement() {
        Map<String, Object> body = parentService.announcementLocalized(
                announcementA.getId(), "hi", actAs(asParentA));
        assertEquals("<translated>", body.get("title"));
    }

    @Test
    public void parentCannotTranslateCrossTenantAnnouncement() {
        User adminUserB = makeUser(tenantB, academicYearIdB, UserRole.ADMIN);

        Announcement announcementB = new Announcement();
        announcementB.setId(UUID.randomUUID());
        announcementB.setTenantId(tenantB);
        announcementB.setAcademicYearId(academicYearIdB);
        announcementB.setTitle("Other school's news");
        announcementB.setContent("Not for tenant A.");
        announcementB.setTargetGrade("ALL");
        announcementB.setCreatedBy(adminUserB.getId());
        announcementB.setCreatedAt(LocalDateTime.now());
        announcementRepository.saveAndFlush(announcementB);

        ParentException ex = assertThrows(ParentException.class, () ->
                parentService.announcementLocalized(announcementB.getId(), "hi", actAs(asParentA)));
        assertEquals(400, ex.status());
    }

    @Test
    public void announcementSpeechRejectsUnsupportedLanguage() {
        ParentException ex = assertThrows(ParentException.class, () ->
                parentService.announcementSpeech(announcementA.getId(), "xx-not-a-lang", actAs(asParentA)));
        assertEquals(400, ex.status());
    }

    @Test
    public void setPreferredLanguage_rejectsUnsupportedCode() {
        ParentException ex = assertThrows(ParentException.class, () ->
                parentService.setPreferredLanguage("xx", actAs(asParentA)));
        assertEquals(400, ex.status());
    }

    @Test
    public void setPreferredLanguage_savesSupportedCode() {
        Map<String, Object> body = parentService.setPreferredLanguage("hi", actAs(asParentA));
        assertEquals("hi", body.get("language"));
        Parent reloaded = parentRepository.findById(parentA.getId()).orElseThrow();
        assertEquals("hi", reloaded.getPreferredLanguage());
    }

    @Test
    public void voiceReply_rejectedForNonParticipant() {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setTenantId(tenantA);
        conversation.setAcademicYearId(academicYearIdA);
        conversation.setStudentId(UUID.randomUUID());
        conversation.setTeacherId(UUID.randomUUID());
        conversationRepository.saveAndFlush(conversation);

        MessagingException ex = assertThrows(MessagingException.class, () ->
                messagingService.voiceReply(parentUserA.getEmail(), conversation.getId(), new byte[] { 0, 1, 2 }, "hi"));
        assertEquals(403, ex.status());
    }

    @Test
    public void voiceReply_transcribesAndTranslatesForParticipant() {
        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantA);
        section.setAcademicYearId(academicYearIdA);
        section.setGradeName("Grade 1");
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantA);
        student.setAcademicYearId(academicYearIdA);
        student.setFirstName("Kid");
        student.setLastName("A");
        student.setClassSection(section);
        student.getParents().add(parentA);
        studentRepository.saveAndFlush(student);

        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setTenantId(tenantA);
        conversation.setAcademicYearId(academicYearIdA);
        conversation.setStudentId(student.getId());
        conversation.setTeacherId(UUID.randomUUID());
        conversationRepository.saveAndFlush(conversation);

        when(translationService.translate("hola profesor", "en")).thenReturn("hello teacher");

        actAs(asParentA);
        MessageRef result = messagingService.voiceReply(parentUserA.getEmail(), conversation.getId(), new byte[] { 0, 1, 2 }, "hi");
        assertEquals("hello teacher", result.body());
    }
}
