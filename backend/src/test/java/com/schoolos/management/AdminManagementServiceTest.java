package com.schoolos.management;

import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.tenant.AcademicYear;
import com.schoolos.tenant.AcademicYearRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
import com.schoolos.user.UserRepository;
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
 * Exercises the admin add-student / add-parent provisioning logic directly
 * through {@link AdminManagementService} — no HTTP, session, or Spring Security.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class AdminManagementServiceTest {

    @Autowired private AdminManagementService adminManagementService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentMetricRepository studentMetricRepository;
    @Autowired private UserRepository userRepository;

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
    public void addStudent_provisionsLoginAndSeedsMetric() {
        String email = "kid-" + UUID.randomUUID() + "@school.edu";
        Student s = adminManagementService.addStudent("Aarav", "Mehta", "R1", schoolClass.getId(),
                email, "Secret123!", tenantId, yearId);

        assertNotNull(s.getUserId());
        assertTrue(userRepository.existsByEmail(email));
        assertTrue(studentMetricRepository.findByStudentId(s.getId()).isPresent());
    }

    @Test
    public void addStudent_withoutLoginLeavesUserIdNull() {
        Student s = adminManagementService.addStudent("Nolo", "Gin", "R2", schoolClass.getId(),
                null, null, tenantId, yearId);
        assertNull(s.getUserId());
        assertTrue(studentMetricRepository.findByStudentId(s.getId()).isPresent());
    }

    @Test
    public void addStudent_duplicateEmailThrows() {
        String email = "dup-" + UUID.randomUUID() + "@school.edu";
        adminManagementService.addStudent("First", "Kid", "R3", schoolClass.getId(), email, "pw12345!", tenantId, yearId);
        assertThrows(IllegalArgumentException.class, () ->
                adminManagementService.addStudent("Second", "Kid", "R4", schoolClass.getId(), email, "pw12345!", tenantId, yearId));
    }

    @Test
    public void addParent_linksToStudentAndProvisionsLogin() {
        Student child = adminManagementService.addStudent("Child", "One", "R5", schoolClass.getId(), null, null, tenantId, yearId);
        String email = "parent-" + UUID.randomUUID() + "@home.com";

        Parent parent = adminManagementService.addParent("Rohan", "Mehta", "+91 9800000000", child.getId(),
                email, "Secret123!", tenantId, yearId);

        assertNotNull(parent.getUserId());
        assertTrue(userRepository.existsByEmail(email));
        // The child now lists this parent.
        Student reloaded = studentRepository.findById(child.getId()).orElseThrow();
        assertTrue(reloaded.getParents().stream().anyMatch(p -> p.getId().equals(parent.getId())));
    }

    @Test
    public void addParent_duplicateEmailThrows() {
        String email = "pdup-" + UUID.randomUUID() + "@home.com";
        adminManagementService.addParent("A", "One", null, null, email, "pw12345!", tenantId, yearId);
        assertThrows(IllegalArgumentException.class, () ->
                adminManagementService.addParent("B", "Two", null, null, email, "pw12345!", tenantId, yearId));
    }

    @Test
    public void addParent_sameParentWithMultipleChildrenReusesOneAccount() {
        String phone = "+91 9812345678";
        Student kid1 = adminManagementService.addStudent("Kid", "One", "M1", schoolClass.getId(), null, null, tenantId, yearId);
        Student kid2 = adminManagementService.addStudent("Kid", "Two", "M2", schoolClass.getId(), null, null, tenantId, yearId);

        // First child: parent gets a login (username = phone).
        Parent p1 = adminManagementService.addParent("Suraj", "Tomar", phone, kid1.getId(), phone, "pw12345!", tenantId, yearId);
        // Second child: same phone → must reuse the SAME parent, not create a duplicate,
        // and not fail trying to re-create the login.
        Parent p2 = adminManagementService.addParent("Suraj", "Tomar", phone, kid2.getId(), phone, "pw12345!", tenantId, yearId);

        assertEquals(p1.getId(), p2.getId(), "same-phone guardian should be reused, not duplicated");

        // One parent account is now linked to BOTH children.
        assertTrue(studentRepository.findById(kid1.getId()).orElseThrow().getParents()
                .stream().anyMatch(p -> p.getId().equals(p1.getId())));
        assertTrue(studentRepository.findById(kid2.getId()).orElseThrow().getParents()
                .stream().anyMatch(p -> p.getId().equals(p1.getId())));
        // The parent portal resolves both kids from that single parent.
        assertEquals(2, studentRepository.findByParentsContaining(p1).size());
    }
}
