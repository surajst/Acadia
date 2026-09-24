package com.concept.attendance;

import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.attendance.data.AttendanceRecordRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Priya teaches 6-A. The register page offered her 6-A, 6-B and 7-A, and the
 * submit accepted whichever she sent -- so any teacher could mark any child in
 * the school. Marking a child absent messages their guardian, so a wrong
 * register does not stay inside the app.
 *
 * <p>The submit tests post <em>straight at the endpoint</em> with another
 * section's student id. Filtering the dropdown is not the fix; it only stops an
 * honest teacher reaching a section she was never offered. Twice now a
 * client-side guard has existed while the server accepted the value behind it,
 * so the rule is asserted where the rule has to live.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class AttendanceScopeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private ClassSection sixB;
    private Student aarav;   // 6-A, Priya's
    private Student neha;    // 6-B, not Priya's
    private String priyaEmail;
    private String headEmail;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("scope-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        sixA = section("Grade 6", "A");
        sixB = section("Grade 6", "B");
        aarav = student("Aarav", sixA);
        neha = student("Neha", sixB);

        priyaEmail = "priya-" + suffix + "@example.com";
        User priya = teacher(priyaEmail, "Priya Sharma", UserRole.TEACHER);
        headEmail = "head-" + suffix + "@example.com";
        teacher(headEmail, "Head Teacher", UserRole.ADMIN);

        SubjectAssignment onlySixA = new SubjectAssignment();
        onlySixA.setId(UUID.randomUUID());
        onlySixA.setTenantId(tenantId);
        onlySixA.setAcademicYearId(yearId);
        onlySixA.setTeacher(priya);
        onlySixA.setClassSection(sixA);
        onlySixA.setSubjectName("Mathematics");
        onlySixA.setHomeClass(true);
        subjectAssignmentRepository.saveAndFlush(onlySixA);
    }

    private ClassSection section(String grade, String name) {
        ClassSection s = new ClassSection();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setGradeName(grade);
        s.setSectionName(name);
        return classSectionRepository.saveAndFlush(s);
    }

    private Student student(String firstName, ClassSection in) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(firstName);
        s.setLastName("Verma");
        s.setClassSection(in);
        return studentRepository.saveAndFlush(s);
    }

    private User teacher(String email, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private MvcResult submit(String email, String role, Student who, ClassSection as) throws Exception {
        return mockMvc.perform(post("/web/teacher/attendance/submit")
                        .with(user(email).roles(role))
                        .with(csrf())
                        .param("studentIds", who.getId().toString())
                        .param("statuses", "ABSENT")
                        .param("classId", as.getId().toString()))
                .andReturn();
    }

    private boolean markedToday(Student who) {
        return attendanceRecordRepository.findAll().stream()
                .anyMatch(a -> a.getStudent() != null
                        && who.getId().equals(a.getStudent().getId())
                        && LocalDate.now().equals(a.getAttendanceDate()));
    }

    /** The test the finding asks for: Priya submits for 6-B. */
    @Test
    void priyaIsRefusedASectionSheIsNotAssignedTo() throws Exception {
        MvcResult result = submit(priyaEmail, "TEACHER", neha, sixB);

        assertEquals(403, result.getResponse().getStatus(),
                "a teacher must not be able to take a register for a section she does not teach");
        assertFalse(markedToday(neha),
                "the refusal has to happen before the write, or 6-B is marked anyway");
    }

    /**
     * Sending 6-A as the classId while naming a 6-B child is the same attempt
     * with the form made to look legitimate. The decision is per student, not
     * per submitted classId, which is why this is refused too.
     */
    @Test
    void aTruthfulClassIdWithSomebodyElsesStudentIsAlsoRefused() throws Exception {
        MvcResult result = submit(priyaEmail, "TEACHER", neha, sixA);

        assertEquals(403, result.getResponse().getStatus(),
                "the classId parameter is not what decides this -- the student's own section is");
        assertFalse(markedToday(neha));
    }

    /** The guard must not stop her taking the register she is there to take. */
    @Test
    void priyaCanStillTakeHerOwnRegister() throws Exception {
        MvcResult result = submit(priyaEmail, "TEACHER", aarav, sixA);

        assertTrue(result.getResponse().getStatus() < 400,
                "6-A is hers, got " + result.getResponse().getStatus());
        assertTrue(markedToday(aarav), "her own register must actually be written");
    }

    /** Covering for an absent teacher is ordinary, so an admin gets all of them. */
    @Test
    void anAdminMayTakeAnySectionsRegister() throws Exception {
        MvcResult result = submit(headEmail, "ADMIN", neha, sixB);

        assertTrue(result.getResponse().getStatus() < 400,
                "an admin covering 6-B must not be refused, got " + result.getResponse().getStatus());
        assertTrue(markedToday(neha));
    }

    /**
     * And the page itself should not offer what the server will refuse. This is
     * the cosmetic half of the fix -- it is asserted separately so that a
     * filtered dropdown can never be mistaken for the rule above.
     */
    @Test
    void theSectionDropdownOffersOnlyHerOwnSections() throws Exception {
        String html = mockMvc.perform(get("/web/teacher/attendance")
                        .with(user(priyaEmail).roles("TEACHER")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(sixA.getId().toString()),
                "6-A is hers and has to be selectable");
        assertFalse(html.contains(sixB.getId().toString()),
                "6-B was offered in the dropdown, which is how this was found");
    }

    /** An admin's dropdown still lists the school. */
    @Test
    void anAdminsDropdownStillListsEverySection() throws Exception {
        String html = mockMvc.perform(get("/web/teacher/attendance")
                        .with(user(headEmail).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains(sixA.getId().toString()) && html.contains(sixB.getId().toString()),
                "filtering by assignment must not narrow an admin's view");
    }
}
