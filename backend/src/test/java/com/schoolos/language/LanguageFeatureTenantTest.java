package com.schoolos.language;

import com.schoolos.announcement.Announcement;
import com.schoolos.announcement.AnnouncementRepository;
import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Conversation;
import com.schoolos.management.ConversationRepository;
import com.schoolos.management.MessagingApiController;
import com.schoolos.management.MobileParentRestController;
import com.schoolos.management.Parent;
import com.schoolos.management.ParentRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.tenant.AcademicYear;
import com.schoolos.tenant.AcademicYearRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
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
    private MobileParentRestController parentController;

    @Autowired
    private MessagingApiController messagingApiController;

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
        ResponseEntity<?> response = parentController.getAnnouncementLocalized(
                announcementA.getId(), "hi", actAs(asParentA));
        assertEquals(200, response.getStatusCode().value());
        assertEquals("<translated>", ((Map<?, ?>) response.getBody()).get("title"));
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

        ResponseEntity<?> response = parentController.getAnnouncementLocalized(
                announcementB.getId(), "hi", actAs(asParentA));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void announcementSpeechRejectsUnsupportedLanguage() {
        ResponseEntity<?> response = parentController.getAnnouncementSpeech(
                announcementA.getId(), "xx-not-a-lang", actAs(asParentA));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void setPreferredLanguage_rejectsUnsupportedCode() {
        ResponseEntity<?> response = parentController.setPreferredLanguage(
                Map.of("language", "xx"), actAs(asParentA));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void setPreferredLanguage_savesSupportedCode() {
        ResponseEntity<?> response = parentController.setPreferredLanguage(
                Map.of("language", "hi"), actAs(asParentA));
        assertEquals(200, response.getStatusCode().value());
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

        MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] { 0, 1, 2 });
        ResponseEntity<?> response = messagingApiController.voiceReply(
                conversation.getId(), audio, "hi", actAs(asParentA));
        assertEquals(403, response.getStatusCode().value());
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

        MockMultipartFile audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] { 0, 1, 2 });
        ResponseEntity<?> response = messagingApiController.voiceReply(
                conversation.getId(), audio, "hi", actAs(asParentA));
        assertEquals(200, response.getStatusCode().value());
        assertEquals("hello teacher", ((Map<?, ?>) response.getBody()).get("body"));
    }
}
