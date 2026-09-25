package com.concept.roster;

import com.concept.academics.data.StudentMetricRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.SchoolType;
import com.concept.tenant.TenantOnboardingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Registering a student creates the student, then the guardian. When the
 * guardian's phone is refused, the student must not be left behind -- a child on
 * the roster with a login provisioned, no guardian attached, and an admin who was
 * told the registration failed is worse than either outcome on its own. The next
 * attempt then looks like a duplicate.
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> Every other test in this
 * area is, and that is exactly why this one has to be separate: a
 * {@code @Transactional} test makes the service join the test's own transaction,
 * which never commits, so the flushed row stays visible whether the service
 * rolls back or not. The assertion would pass against a service with no
 * transaction at all. It cleans up after itself instead, against its own
 * freshly-created school so nothing else is touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-mode=true")
class RegisterStudentRollbackTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentMetricRepository studentMetricRepository;

    private String adminEmail;
    private UUID tenantId;
    private UUID sectionId;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminEmail = "admin-" + suffix + "@example.com";
        var school = onboardingService.createSchool("Demo SSC " + suffix, "rb" + suffix,
                adminEmail, "AdminPass123!", "Suraj Demo", SchoolType.SECONDARY);
        tenantId = school.tenant.getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(school.academicYear.getId());
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        sectionId = classSectionRepository.saveAndFlush(section).getId();
    }

    @AfterEach
    void cleanUp() {
        // This test commits, so it puts its own rows back.
        List<Student> mine = studentRepository.findByTenantId(tenantId);
        for (Student s : mine) {
            // Registering a student seeds their metrics row, which holds a
            // foreign key to them -- so it goes first.
            studentMetricRepository.findByStudentId(s.getId())
                    .ifPresent(studentMetricRepository::delete);
        }
        if (!mine.isEmpty()) {
            studentRepository.deleteAll(mine);
        }
        classSectionRepository.findByIdAndTenantId(sectionId, tenantId)
                .ifPresent(classSectionRepository::delete);
    }

    /**
     * The guardian's phone fails validation after the student has been created
     * and flushed, so this only holds if the whole registration is one
     * transaction.
     */
    @Test
    void aGuardianRefusedAfterTheStudentIsCreatedLeavesNoStudentBehind() throws Exception {
        mockMvc.perform(post("/web/admin/student/add")
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("firstName", "Aarav")
                        .param("lastName", "Verma")
                        .param("rollNumber", "6A-41")
                        .param("schoolClassId", sectionId.toString())
                        .param("guardianFirstName", "Ramesh")
                        .param("guardianLastName", "Verma")
                        .param("guardianPhone", "abc123"))
                .andReturn();

        assertTrue(studentRepository.findByTenantId(tenantId).isEmpty(),
                "the student was created before the guardian was refused, so a registration "
                        + "that failed must not leave the child on the roster");
    }

    /** And a registration that succeeds does commit, so the rollback is not over-broad. */
    @Test
    void anAcceptedRegistrationIsCommitted() throws Exception {
        mockMvc.perform(post("/web/admin/student/add")
                        .with(user(adminEmail).roles("ADMIN"))
                        .with(csrf())
                        .param("firstName", "Aarav")
                        .param("lastName", "Verma")
                        .param("rollNumber", "6A-41")
                        .param("schoolClassId", sectionId.toString())
                        .param("guardianFirstName", "Ramesh")
                        .param("guardianLastName", "Verma")
                        .param("guardianPhone", "+91 98765 43210"))
                .andReturn();

        assertEquals(1, studentRepository.findByTenantId(tenantId).size(),
                "an ordinary registration has to actually persist");
    }
}
