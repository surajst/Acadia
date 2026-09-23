package com.concept.assignment;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assigning Priya to Grade 6-A for Mathematics made Grade 6-A Science
 * impossible for her: the duplicate check was keyed on (teacher, section) and
 * ignored the subject entirely. A teacher taking two subjects for one class is
 * ordinary, not a mistake.
 *
 * <p>What the old key was really protecting -- one home class teacher per
 * section -- it protected by accident, so that is now its own rule.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class SubjectAssignmentRulesTest {

    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sectionA;
    private User priya;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("sa-" + UUID.randomUUID());
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

        sectionA = new ClassSection();
        sectionA.setId(UUID.randomUUID());
        sectionA.setTenantId(tenantId);
        sectionA.setAcademicYearId(yearId);
        sectionA.setGradeName("Grade 6");
        sectionA.setSectionName("A");
        sectionA = classSectionRepository.saveAndFlush(sectionA);

        priya = teacher("priya@example.com", "Priya Demo");
    }

    private User teacher(String email, String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(UserRole.TEACHER);
        return userRepository.saveAndFlush(u);
    }

    /** The reported case. */
    @Test
    void aTeacherCanTakeTwoSubjectsForTheSameClass() {
        assignmentService.assignSubject(priya.getId(), sectionA.getId(), "Mathematics", true, tenantId);

        assertDoesNotThrow(() ->
                assignmentService.assignSubject(priya.getId(), sectionA.getId(), "Science", false, tenantId));
    }

    /** The same subject twice is still a duplicate. */
    @Test
    void theSameSubjectTwiceIsStillRefused() {
        assignmentService.assignSubject(priya.getId(), sectionA.getId(), "Mathematics", false, tenantId);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                assignmentService.assignSubject(priya.getId(), sectionA.getId(), "Mathematics", false, tenantId));

        assertTrue(e.getMessage().contains("Priya Demo"), "name the teacher: " + e.getMessage());
        assertTrue(e.getMessage().contains("Grade 6 A"), "name the class: " + e.getMessage());
        assertFalse(e.getMessage().contains(priya.getId().toString()),
                "an internal id is not an explanation: " + e.getMessage());
    }

    /** A section has one home class teacher, and the message says who. */
    @Test
    void onlyOneTeacherCanBeHomeClassForASection() {
        assignmentService.assignSubject(priya.getId(), sectionA.getId(), "Mathematics", true, tenantId);
        User vikram = teacher("vikram@example.com", "Vikram Rao");

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                assignmentService.assignSubject(vikram.getId(), sectionA.getId(), "Science", true, tenantId));
        assertTrue(e.getMessage().contains("Priya Demo"), "name who already holds it: " + e.getMessage());
    }

    /** A second teacher on the section is fine as long as they are not home class. */
    @Test
    void aSecondTeacherCanTakeTheSectionForAnotherSubject() {
        assignmentService.assignSubject(priya.getId(), sectionA.getId(), "Mathematics", true, tenantId);
        User vikram = teacher("vikram2@example.com", "Vikram Rao");

        assertDoesNotThrow(() ->
                assignmentService.assignSubject(vikram.getId(), sectionA.getId(), "Science", false, tenantId));
    }
}
