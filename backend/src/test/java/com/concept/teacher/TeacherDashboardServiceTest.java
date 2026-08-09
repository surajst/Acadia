package com.concept.teacher;
import com.concept.teacher.app.TeacherDashboardService;
import com.concept.student.data.AcademicSubmissionRepository;
import com.concept.student.data.AcademicSubmission;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.assignment.data.SubjectAssignment;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the ownership scoping in {@link TeacherDashboardService}: a plain
 * TEACHER only sees their own students' pending submissions, while ADMIN keeps
 * the tenant-wide oversight view. This is the filtering that previously leaked
 * across teachers, so it now has a direct unit test.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class TeacherDashboardServiceTest {

    @Autowired private TeacherDashboardService teacherDashboardService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;
    @Autowired private AcademicSubmissionRepository academicSubmissionRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sectionA;
    private ClassSection sectionB;
    private User teacher;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Dash Tenant");
        tenant.setSubdomain("dash-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);

        sectionA = section("Grade 5", "A");
        sectionB = section("Grade 5", "B");

        teacher = new User();
        teacher.setId(UUID.randomUUID());
        teacher.setTenantId(tenantId);
        teacher.setAcademicYearId(yearId);
        teacher.setEmail("teacher-" + tenantId.toString().substring(0, 8) + "@school.edu");
        teacher.setPasswordHash("x");
        teacher.setFullName("Test Teacher");
        teacher.setRole(UserRole.TEACHER);
        teacher.setActive(true);
        userRepository.saveAndFlush(teacher);

        // Teacher is assigned to section A only.
        SubjectAssignment assignment = new SubjectAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(tenantId);
        assignment.setAcademicYearId(yearId);
        assignment.setTeacher(teacher);
        assignment.setClassSection(sectionA);
        assignment.setSubjectName("Mathematics");
        assignment.setHomeClass(true);
        subjectAssignmentRepository.saveAndFlush(assignment);
    }

    private ClassSection section(String grade, String name) {
        ClassSection cs = new ClassSection();
        cs.setId(UUID.randomUUID());
        cs.setTenantId(tenantId);
        cs.setAcademicYearId(yearId);
        cs.setGradeName(grade);
        cs.setSectionName(name);
        return classSectionRepository.saveAndFlush(cs);
    }

    private Student student(String name, ClassSection section) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(name);
        s.setLastName("Test");
        s.setClassSection(section);
        return studentRepository.saveAndFlush(s);
    }

    private void pendingSubmission(Student s) {
        AcademicSubmission sub = new AcademicSubmission();
        sub.setId(UUID.randomUUID());
        sub.setStudentId(s.getId());
        sub.setSkillName("Fractions");
        sub.setXpBounty(50);
        sub.setStatus("PENDING");
        sub.setSubmittedAt(LocalDateTime.now());
        academicSubmissionRepository.saveAndFlush(sub);
    }

    @Test
    public void teacherSeesOnlyOwnStudentsSubmissions() {
        Student mine = student("Mine", sectionA);
        Student notMine = student("NotMine", sectionB);
        pendingSubmission(mine);
        pendingSubmission(notMine);

        TeacherDashboardService.VerificationQueues q =
                teacherDashboardService.buildVerificationQueues(teacher.getEmail(), "TEACHER", tenantId);

        List<TeacherDashboardService.MilestoneSubmissionDto> subs = q.pendingSubmissions();
        assertEquals(1, subs.size());
        assertEquals("Mine Test", subs.get(0).getStudentName());
    }

    @Test
    public void adminSeesAllTenantSubmissions() {
        pendingSubmission(student("Mine", sectionA));
        pendingSubmission(student("Other", sectionB));

        TeacherDashboardService.VerificationQueues q =
                teacherDashboardService.buildVerificationQueues("someone@admin.edu", "ADMIN", tenantId);

        assertEquals(2, q.pendingSubmissions().size());
    }

    @Test
    public void resolveOwnStudentIdsReturnsOnlyAssignedSectionStudents() {
        Student mine = student("Mine", sectionA);
        student("NotMine", sectionB);

        var ids = teacherDashboardService.resolveOwnStudentIds(teacher.getEmail());

        assertEquals(1, ids.size());
        assertTrue(ids.contains(mine.getId()));
    }
}
