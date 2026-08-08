package com.concept.messaging.app;

import com.concept.management.Conversation;
import com.concept.management.ConversationRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the structural tenant isolation of the messaging slice: a teacher from
 * another school cannot open a conversation, because it is resolved via
 * {@code findByIdAndTenantId} scoped to the caller's own tenant.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class MessagingServiceTest {

    @Autowired private MessagingService messagingService;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantA, yearA, tenantB, yearB;
    private User teacherA, teacherB;
    private Conversation convA;

    private UUID tenant() {
        Tenant t = new Tenant();
        t.setId(UUID.randomUUID());
        t.setName("T " + t.getId());
        t.setSubdomain("t-" + t.getId().toString().substring(0, 8));
        t.setActive(true);
        t.setCreatedAt(Instant.now());
        return tenantRepository.saveAndFlush(t).getId();
    }

    private UUID year(UUID tenantId) {
        AcademicYear y = new AcademicYear();
        y.setId(UUID.randomUUID());
        y.setTenantId(tenantId);
        y.setName("2026");
        y.setStartDate(LocalDate.of(2026, 1, 1));
        y.setEndDate(LocalDate.of(2026, 12, 31));
        y.setCurrent(true);
        return academicYearRepository.saveAndFlush(y).getId();
    }

    private User teacher(UUID tenantId, UUID yearId) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail("teacher-" + UUID.randomUUID() + "@ex.com");
        u.setPasswordHash("hash");
        u.setFullName("Teacher");
        u.setRole(UserRole.TEACHER);
        u.setActive(true);
        return userRepository.saveAndFlush(u);
    }

    @BeforeEach
    public void setup() {
        tenantA = tenant(); yearA = year(tenantA);
        tenantB = tenant(); yearB = year(tenantB);
        teacherA = teacher(tenantA, yearA);
        teacherB = teacher(tenantB, yearB);

        convA = new Conversation();
        convA.setId(UUID.randomUUID());
        convA.setTenantId(tenantA);
        convA.setAcademicYearId(yearA);
        convA.setStudentId(UUID.randomUUID());
        convA.setTeacherId(teacherA.getId());
        conversationRepository.saveAndFlush(convA);
    }

    @Test
    public void owningTeacherCanOpenThread() {
        assertTrue(messagingService.getThread(teacherA.getEmail(), convA.getId()).isEmpty(),
                "owning teacher sees the (empty) thread, no exception");
    }

    @Test
    public void teacherFromAnotherTenantCannotOpenThread() {
        MessagingException ex = assertThrows(MessagingException.class,
                () -> messagingService.getThread(teacherB.getEmail(), convA.getId()));
        // Resolved out of existence for the wrong tenant — indistinguishable from "not found".
        assertEquals(400, ex.status());
    }
}
