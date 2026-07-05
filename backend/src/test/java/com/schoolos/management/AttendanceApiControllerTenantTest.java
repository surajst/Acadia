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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Covers the fix for the attendance IDOR: a teacher must be assigned
// (via SubjectAssignment) to a class section before they can view or
// submit attendance for it — previously neither endpoint checked this
// at all, letting any teacher read or write any tenant's attendance.
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class AttendanceApiControllerTenantTest {

    @Autowired
    private AttendanceApiController attendanceApiController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private SubjectAssignmentRepository subjectAssignmentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    private User teacherA;
    private ClassSection sectionA;
    private ClassSection sectionB;
    private Student studentB;
    private Authentication authA;

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
        UUID tenantA = makeTenant().getId();
        UUID tenantB = makeTenant().getId();
        UUID academicYearIdA = makeAcademicYear(tenantA);
        UUID academicYearIdB = makeAcademicYear(tenantB);

        teacherA = new User();
        teacherA.setId(UUID.randomUUID());
        teacherA.setTenantId(tenantA);
        teacherA.setAcademicYearId(academicYearIdA);
        teacherA.setEmail("teacher-a-" + UUID.randomUUID() + "@example.com");
        teacherA.setPasswordHash("hash");
        teacherA.setFullName("Teacher A");
        teacherA.setRole(UserRole.TEACHER);
        userRepository.saveAndFlush(teacherA);

        sectionA = new ClassSection();
        sectionA.setId(UUID.randomUUID());
        sectionA.setTenantId(tenantA);
        sectionA.setAcademicYearId(academicYearIdA);
        sectionA.setGradeName("Grade 1");
        sectionA.setSectionName("A");
        classSectionRepository.saveAndFlush(sectionA);

        SubjectAssignment assignment = new SubjectAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTeacher(teacherA);
        assignment.setClassSection(sectionA);
        assignment.setSubjectName("Mathematics");
        assignment.setHomeClass(true);
        assignment.setTenantId(tenantA);
        assignment.setAcademicYearId(academicYearIdA);
        subjectAssignmentRepository.saveAndFlush(assignment);

        sectionB = new ClassSection();
        sectionB.setId(UUID.randomUUID());
        sectionB.setTenantId(tenantB);
        sectionB.setAcademicYearId(academicYearIdB);
        sectionB.setGradeName("Grade 1");
        sectionB.setSectionName("A");
        classSectionRepository.saveAndFlush(sectionB);

        studentB = new Student();
        studentB.setId(UUID.randomUUID());
        studentB.setTenantId(tenantB);
        studentB.setAcademicYearId(academicYearIdB);
        studentB.setFirstName("Other");
        studentB.setLastName("Student");
        studentB.setClassSection(sectionB);
        studentRepository.saveAndFlush(studentB);

        authA = new UsernamePasswordAuthenticationToken(teacherA.getEmail(), null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TEACHER")));
        SecurityContextHolder.getContext().setAuthentication(authA);
    }

    @Test
    public void getTodayAttendance_ownSection_succeeds() {
        ResponseEntity<?> response = attendanceApiController.getTodayAttendance(sectionA.getId(), authA);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    public void getTodayAttendance_otherTenantSection_rejected() {
        ResponseEntity<?> response = attendanceApiController.getTodayAttendance(sectionB.getId(), authA);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(Map.of("error", "Section not found"), response.getBody());
    }

    @Test
    public void submitAttendance_otherTenantStudent_isSkippedAndNotPersisted() {
        var entry = new AttendanceApiController.AttendanceEntry(studentB.getId(), AttendanceStatus.ABSENT, null);
        var payload = new AttendanceApiController.AttendancePayload(List.of(entry));

        ResponseEntity<?> response = attendanceApiController.submitAttendance(payload, authA);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "success", "saved", 0, "skipped", 1), response.getBody());
        assertTrue(attendanceRepository.findByClassSectionAndAttendanceDate(sectionB, LocalDate.now()).isEmpty());
    }
}
