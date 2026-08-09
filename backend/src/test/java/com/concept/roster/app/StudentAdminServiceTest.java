package com.concept.roster.app;

import com.concept.academics.StudentMetricRepository;
import com.concept.shared.data.SchoolClass;
import com.concept.shared.data.SchoolClassRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the admin student/parent provisioning logic directly through
 * {@link StudentAdminService} — no HTTP, session, or Spring Security. Also pins
 * the structural tenant isolation: an edit against the wrong tenant is not found.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class StudentAdminServiceTest {

    @Autowired private StudentAdminService studentAdminService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentMetricRepository studentMetricRepository;
    @Autowired private UserRepository userRepository;

    private static final Authentication NO_AUTH = null;

    private UUID tenantId;
    private UUID yearId;
    private SchoolClass schoolClass;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Mgmt Tenant");
        tenant.setSubdomain("mg-" + tenantId.toString().substring(0, 8));
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

        schoolClass = new SchoolClass();
        schoolClass.setId(UUID.randomUUID());
        schoolClass.setTenantId(tenantId);
        schoolClass.setAcademicYearId(yearId);
        schoolClass.setGradeLevel("Grade 5");
        schoolClass.setSectionName("A");
        schoolClass.setRoomNumber("101");
        schoolClass.setTotalCapacity(30);
        schoolClassRepository.saveAndFlush(schoolClass);
    }

    @Test
    public void addStudent_autoProvisionsLoginFromRollNumberAndSeedsMetric() {
        String roll = "R-" + UUID.randomUUID().toString().substring(0, 8);
        String creds = studentAdminService.addStudent("Aarav", "Mehta", roll, schoolClass.getId(),
                null, null, null, null, null, tenantId, yearId, NO_AUTH);

        assertNotNull(creds, "auto-provisioned login should be relayed back");
        assertTrue(creds.contains(roll), "credentials should mention the roll-number username");
        assertTrue(userRepository.existsByEmail(roll));
        Student s = studentRepository.findByTenantIdAndRollNumber(tenantId, roll).orElseThrow();
        assertNotNull(s.getUserId());
        assertTrue(studentMetricRepository.findByStudentId(s.getId()).isPresent());
    }

    @Test
    public void addStudent_explicitDuplicateEmailThrows() {
        String email = "dup-" + UUID.randomUUID() + "@school.edu";
        studentAdminService.addStudent("First", "Kid", "R3", schoolClass.getId(),
                email, "pw12345!", null, null, null, tenantId, yearId, NO_AUTH);
        assertThrows(IllegalArgumentException.class, () ->
                studentAdminService.addStudent("Second", "Kid", "R4", schoolClass.getId(),
                        email, "pw12345!", null, null, null, tenantId, yearId, NO_AUTH));
    }

    @Test
    public void addParent_linksToStudentAndProvisionsLogin() {
        String roll = "R5-" + UUID.randomUUID().toString().substring(0, 6);
        studentAdminService.addStudent("Child", "One", roll, schoolClass.getId(),
                null, null, null, null, null, tenantId, yearId, NO_AUTH);
        Student child = studentRepository.findByTenantIdAndRollNumber(tenantId, roll).orElseThrow();
        String email = "parent-" + UUID.randomUUID() + "@home.com";

        UUID parentId = studentAdminService.addParent("Rohan", "Mehta", "+91 9800000000", child.getId(),
                email, "Secret123!", tenantId, yearId, NO_AUTH);

        assertTrue(userRepository.existsByEmail(email));
        Student reloaded = studentRepository.findById(child.getId()).orElseThrow();
        assertTrue(reloaded.getParents().stream().anyMatch(p -> p.getId().equals(parentId)));
    }

    @Test
    public void addParent_duplicateEmailThrows() {
        String email = "pdup-" + UUID.randomUUID() + "@home.com";
        studentAdminService.addParent("A", "One", null, null, email, "pw12345!", tenantId, yearId, NO_AUTH);
        assertThrows(IllegalArgumentException.class, () ->
                studentAdminService.addParent("B", "Two", null, null, email, "pw12345!", tenantId, yearId, NO_AUTH));
    }

    @Test
    public void addParent_sameParentWithMultipleChildrenReusesOneAccount() {
        String phone = "+91 9812345678";
        studentAdminService.addStudent("Kid", "One", "M1", schoolClass.getId(),
                null, null, null, null, null, tenantId, yearId, NO_AUTH);
        studentAdminService.addStudent("Kid", "Two", "M2", schoolClass.getId(),
                null, null, null, null, null, tenantId, yearId, NO_AUTH);
        Student kid1 = studentRepository.findByTenantIdAndRollNumber(tenantId, "M1").orElseThrow();
        Student kid2 = studentRepository.findByTenantIdAndRollNumber(tenantId, "M2").orElseThrow();

        UUID p1 = studentAdminService.addParent("Suraj", "Tomar", phone, kid1.getId(), phone, "pw12345!", tenantId, yearId, NO_AUTH);
        UUID p2 = studentAdminService.addParent("Suraj", "Tomar", phone, kid2.getId(), phone, "pw12345!", tenantId, yearId, NO_AUTH);

        assertEquals(p1, p2, "same-phone guardian should be reused, not duplicated");
        assertTrue(studentRepository.findById(kid1.getId()).orElseThrow().getParents()
                .stream().anyMatch(p -> p.getId().equals(p1)));
        assertTrue(studentRepository.findById(kid2.getId()).orElseThrow().getParents()
                .stream().anyMatch(p -> p.getId().equals(p1)));
    }

    @Test
    public void updateStudent_fromAnotherTenantIsNotFound() {
        String roll = "X-" + UUID.randomUUID().toString().substring(0, 8);
        studentAdminService.addStudent("Iso", "Late", roll, schoolClass.getId(),
                null, null, null, null, null, tenantId, yearId, NO_AUTH);
        Student s = studentRepository.findByTenantIdAndRollNumber(tenantId, roll).orElseThrow();

        UUID otherTenant = UUID.randomUUID();
        assertThrows(StudentProfileNotFoundException.class, () ->
                studentAdminService.updateStudent(s.getId(), otherTenant, yearId,
                        "Hacked", "Name", "R999", null, null, null, null, NO_AUTH));

        // The record is untouched.
        Student reloaded = studentRepository.findById(s.getId()).orElseThrow();
        assertEquals("Iso", reloaded.getFirstName());
    }
}
