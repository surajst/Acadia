package com.concept;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.concept.academics.data.StudentMetric;
import com.concept.academics.data.StudentMetricRepository;
import com.concept.announcement.Announcement;
import com.concept.announcement.AnnouncementService;
import com.concept.attendance.app.AttendanceFormView;
import com.concept.attendance.app.AttendanceService;
import com.concept.notification.data.NotificationRepository;
import com.concept.parent.app.ParentDashboardService;
import com.concept.parent.app.ParentException;
import com.concept.parent.app.ParentService;
import com.concept.parent.app.ParentDashboardService.QuestView;
import com.concept.parent.data.ParentQuest;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.ParentRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.student.app.StudentService;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;

/**
 * Flows that worked on seeded data and failed when done live, on camera.
 *
 * <p>One family: a parent with two children in Grade 6 (Aarav in 6-A, Diya in
 * 6-B), each child with a login.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class LiveFlowRegressionTest {

    @Autowired private StudentService studentService;
    @Autowired private ParentDashboardService parentDashboardService;
    @Autowired private ParentService parentService;
    @Autowired private StudentMetricRepository studentMetricRepository;
    @Autowired private AnnouncementService announcementService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ParentQuestRepository parentQuestRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParentRepository parentRepository;
    @Autowired private UserRepository userRepository;

    private String suffix;
    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private Parent parent;
    private User parentUser;
    private Student aarav;
    private Authentication aaravAuth;
    private Authentication parentAuth;
    private Authentication adminAuth;
    private User adminUser;

    @BeforeEach
    void setup() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Live Flow School");
        tenant.setSubdomain("live-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.now().minusMonths(3));
        year.setEndDate(LocalDate.now().plusMonths(9));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        sixA = section("A");
        ClassSection sixB = section("B");

        parentUser = person("parent", UserRole.PARENT);
        parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setAcademicYearId(yearId);
        parent.setFirstName("Rakesh");
        parent.setLastName("Verma");
        parent.setUserId(parentUser.getId());
        parent = parentRepository.saveAndFlush(parent);
        parentAuth = auth(parentUser.getEmail(), "PARENT");

        User aaravUser = person("aarav", UserRole.STUDENT);
        aarav = pupil("Aarav", "Verma", "6A-02", sixA, aaravUser.getId());
        aaravAuth = auth(aaravUser.getEmail(), "STUDENT");
        pupil("Diya", "Verma", "6B-01", sixB, person("diya", UserRole.STUDENT).getId());

        adminUser = person("admin", UserRole.ADMIN);
        adminAuth = auth(adminUser.getEmail(), "ADMIN");

        StudentMetric metric = new StudentMetric();
        metric.setId(UUID.randomUUID());
        metric.setStudent(aarav);
        metric.setTenantId(tenantId);
        metric.setAcademicYearId(yearId);
        metric.setSchoolXp(0);
        metric.setParentXp(0);
        metric.setActiveStreak(0);
        studentMetricRepository.saveAndFlush(metric);
    }

    // ── A quest claimed on the phone reaches the parent ──────────────────────

    @Test
    void aQuestClaimedFromThePhoneIsWaitingInTheParentsApprovalList() {
        ParentQuest quest = quest("PENDING");

        studentService.claimQuestApi(quest.getId(), aaravAuth);

        assertEquals("COMPLETED_AWAITING_APPROVAL",
                parentQuestRepository.findByIdAndTenantId(quest.getId(), tenantId).orElseThrow().getStatus());
        assertTrue(awaitingIds().contains(quest.getId()),
                "the parent's approval list must show the claim");
    }

    @Test
    void aQuestClaimedFromThePhoneBeforeTheFixStillReachesTheParent() {
        ParentQuest stuck = quest("AWAITING_APPROVAL");

        assertTrue(awaitingIds().contains(stuck.getId()),
                "rows already stored with the old status are listed too");
    }

    // ── A quest is approved, and paid, once ─────────────────────────────────

    @Test
    void approvingAQuestTwicePaysTheXpOnce() {
        ParentQuest quest = quest("COMPLETED_AWAITING_APPROVAL");

        parentService.approveQuestApi(quest.getId(), parentAuth);
        ParentException second = assertThrows(ParentException.class,
                () -> parentService.approveQuestWeb(quest.getId(), parentAuth));

        assertEquals(409, second.status());
        assertEquals("APPROVED",
                parentQuestRepository.findByIdAndTenantId(quest.getId(), tenantId).orElseThrow().getStatus());
        assertEquals(50, studentMetricRepository.findByStudentId(aarav.getId()).orElseThrow().getParentXp(),
                "the 50 XP bounty is paid once, not twice");
    }

    @Test
    void aQuestTheChildHasNotClaimedCannotBeApproved() {
        ParentQuest quest = quest("PENDING");

        ParentException e = assertThrows(ParentException.class,
                () -> parentService.approveQuestApi(quest.getId(), parentAuth));

        assertEquals(409, e.status());
        assertEquals("PENDING",
                parentQuestRepository.findByIdAndTenantId(quest.getId(), tenantId).orElseThrow().getStatus());
    }

    @Test
    void aQuestClaimedUnderTheOldPhoneStatusCanStillBeApproved() {
        ParentQuest quest = quest("AWAITING_APPROVAL");

        parentService.approveQuestApi(quest.getId(), parentAuth);

        assertEquals("APPROVED",
                parentQuestRepository.findByIdAndTenantId(quest.getId(), tenantId).orElseThrow().getStatus());
    }

    // ── One announcement, one notification, however many children ──────────

    @Test
    void aParentOfTwoChildrenIsNotifiedOfAnAnnouncementOnce() {
        Announcement a = new Announcement();
        a.setTenantId(tenantId);
        a.setAcademicYearId(yearId);
        a.setTitle("Sports Day on Friday");
        a.setContent("Wear house colours.");
        a.setTargetGrade("ALL");
        a.setCreatedBy(adminUser.getId());

        Announcement saved = announcementService.createAnnouncement(a);

        long rows = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(parentUser.getId()).stream()
                .filter(n -> saved.getId().equals(n.getRelatedEntityId()))
                .count();
        assertEquals(1, rows, "two children, one notice");
    }

    // ── The register keeps its order ─────────────────────────────────────────

    @Test
    void theRegisterIsInRollNumberOrderAndAnEditDoesNotMoveAnyone() {
        pupil("Zara", "Khan", "6A-01", sixA, null);
        pupil("Bilal", "Shah", "6A-03", sixA, null);

        assertEquals(List.of("6A-01", "6A-02", "6A-03"), registerRolls());

        // Edit the middle pupil. On Postgres an UPDATE rewrites the row, which
        // is what used to move them down an unordered list.
        aarav.setLastName("Verma-Rao");
        studentRepository.saveAndFlush(aarav);

        assertEquals(List.of("6A-01", "6A-02", "6A-03"), registerRolls());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private List<UUID> awaitingIds() {
        return parentDashboardService.dashboard(parentAuth).orElseThrow()
                .awaitingQuests().stream().map(QuestView::id).toList();
    }

    private List<String> registerRolls() {
        AttendanceFormView form = attendanceService.buildForm(tenantId, sixA.getId(), adminAuth);
        return form.students().stream().map(AttendanceFormView.StudentRow::rollNumber).toList();
    }

    private ParentQuest quest(String status) {
        ParentQuest q = new ParentQuest();
        q.setId(UUID.randomUUID());
        q.setTenantId(tenantId);
        q.setAcademicYearId(yearId);
        q.setStudent(aarav);
        q.setParent(parent);
        q.setTaskDescription("Tidy your room");
        q.setXpBounty(50);
        q.setStatus(status);
        return parentQuestRepository.saveAndFlush(q);
    }

    private ClassSection section(String name) {
        ClassSection cs = new ClassSection();
        cs.setId(UUID.randomUUID());
        cs.setTenantId(tenantId);
        cs.setAcademicYearId(yearId);
        cs.setGradeName("Grade 6");
        cs.setSectionName(name);
        return classSectionRepository.saveAndFlush(cs);
    }

    private User person(String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(name + "-" + suffix + "@example.com");
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Student pupil(String first, String last, String roll, ClassSection in, UUID userId) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName(last);
        s.setRollNumber(roll);
        s.setClassSection(in);
        s.setUserId(userId);
        s.getParents().add(parent);
        return studentRepository.saveAndFlush(s);
    }

    private static Authentication auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
