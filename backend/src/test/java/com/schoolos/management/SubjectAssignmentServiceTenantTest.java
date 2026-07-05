package com.schoolos.management;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Covers the cross-tenant IDOR fix: assignSubject/removeAssignment/
// getAssignmentsForTeacher/getAssignmentsForClass must reject callers whose
// tenantId doesn't match the teacher's/section's tenant.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class SubjectAssignmentServiceTenantTest {

    @Autowired
    private SubjectAssignmentService subjectAssignmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private SubjectAssignmentRepository assignmentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID academicYearId;
    private User teacherA;
    private ClassSection sectionA;

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

    @BeforeEach
    public void setup() {
        tenantA = makeTenant().getId();
        tenantB = makeTenant().getId();
        academicYearId = makeAcademicYear(tenantA);

        teacherA = new User();
        teacherA.setId(UUID.randomUUID());
        teacherA.setTenantId(tenantA);
        teacherA.setAcademicYearId(academicYearId);
        teacherA.setEmail("teacher-a-" + UUID.randomUUID() + "@example.com");
        teacherA.setPasswordHash("hash");
        teacherA.setFullName("Teacher A");
        teacherA.setRole(UserRole.TEACHER);
        userRepository.saveAndFlush(teacherA);

        sectionA = new ClassSection();
        sectionA.setId(UUID.randomUUID());
        sectionA.setTenantId(tenantA);
        sectionA.setAcademicYearId(academicYearId);
        sectionA.setGradeName("Grade 1");
        sectionA.setSectionName("A");
        classSectionRepository.saveAndFlush(sectionA);
    }

    @Test
    public void assignSubject_sameTenant_succeeds() {
        SubjectAssignment saved = subjectAssignmentService.assignSubject(
                teacherA.getId(), sectionA.getId(), "Mathematics", true, tenantA);

        assertNotNull(saved.getId());
        assertEquals(tenantA, saved.getTenantId());
    }

    @Test
    public void assignSubject_crossTenantTeacher_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                subjectAssignmentService.assignSubject(
                        teacherA.getId(), sectionA.getId(), "Mathematics", true, tenantB));
    }

    @Test
    public void getAssignmentsForTeacher_crossTenant_throws() {
        subjectAssignmentService.assignSubject(teacherA.getId(), sectionA.getId(), "Mathematics", true, tenantA);

        assertThrows(IllegalArgumentException.class, () ->
                subjectAssignmentService.getAssignmentsForTeacher(teacherA.getId(), tenantB));
    }

    @Test
    public void getAssignmentsForTeacher_sameTenant_returnsAssignment() {
        subjectAssignmentService.assignSubject(teacherA.getId(), sectionA.getId(), "Mathematics", true, tenantA);

        List<SubjectAssignment> result = subjectAssignmentService.getAssignmentsForTeacher(teacherA.getId(), tenantA);
        assertEquals(1, result.size());
    }

    @Test
    public void getAssignmentsForClass_crossTenant_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                subjectAssignmentService.getAssignmentsForClass(sectionA.getId(), tenantB));
    }

    @Test
    public void removeAssignment_crossTenant_isNoOp() {
        SubjectAssignment saved = subjectAssignmentService.assignSubject(
                teacherA.getId(), sectionA.getId(), "Mathematics", true, tenantA);

        subjectAssignmentService.removeAssignment(saved.getId(), tenantB);

        assertTrue(assignmentRepository.findById(saved.getId()).isPresent());
    }

    @Test
    public void removeAssignment_sameTenant_deletes() {
        SubjectAssignment saved = subjectAssignmentService.assignSubject(
                teacherA.getId(), sectionA.getId(), "Mathematics", true, tenantA);

        subjectAssignmentService.removeAssignment(saved.getId(), tenantA);

        assertFalse(assignmentRepository.findById(saved.getId()).isPresent());
    }
}
