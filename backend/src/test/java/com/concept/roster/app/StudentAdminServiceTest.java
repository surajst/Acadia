package com.concept.roster.app;

import com.concept.academics.StudentMetricRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
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
    @Autowired private ClassSectionRepository classSectionRepository;

    private static final Authentication NO_AUTH = null;

    private UUID tenantId;
    private UUID yearId;
    private String subdomain;
    private SchoolClass schoolClass;
    private ClassSection classSection;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Mgmt Tenant");
        subdomain = "mg-" + tenantId.toString().substring(0, 8);
        tenant.setSubdomain(subdomain);
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

        classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(yearId);
        classSection.setGradeName("Grade 5");
        classSection.setSectionName("A");
        classSectionRepository.saveAndFlush(classSection);
    }

    @Test
    public void addStudent_autoProvisionsLoginQualifiedBySchoolAndSeedsMetric() {
        String roll = "R-" + UUID.randomUUID().toString().substring(0, 8);
        String creds = studentAdminService.addStudent("Aarav", "Mehta", roll, schoolClass.getId(),
                null, null, null, null, null, tenantId, yearId, NO_AUTH);

        // Usernames are firstname + roll number, qualified by the school's
        // subdomain and lowercased. The bare roll number used to be the whole
        // username, which is globally unique across every school -- so the
        // second school to register a given roll silently got no login at all.
        String expected = "aarav" + roll.toLowerCase() + "@" + subdomain;

        assertNotNull(creds, "auto-provisioned login should be relayed back");
        assertTrue(creds.contains(expected),
                "credentials should carry the school-qualified username, was: " + creds);
        assertTrue(userRepository.existsByEmail(expected));
        assertFalse(userRepository.existsByEmail(roll),
                "the bare roll number must not be claimed as a global username");
        Student s = studentRepository.findByTenantIdAndRollNumber(tenantId, roll).orElseThrow();
        assertNotNull(s.getUserId());
        assertTrue(studentMetricRepository.findByStudentId(s.getId()).isPresent());
    }

    /**
     * The recovery path. resetStudentLogin used to key a new login on the bare
     * roll number, so an admin trying to restore access for a locked-out
     * student got "roll number already in use" because a DIFFERENT school
     * happened to have that roll -- the failure landed on the one path that
     * exists to fix the problem.
     */
    @Test
    public void resetStudentLogin_issuesSchoolQualifiedUsername_evenWhenAnotherSchoolHasTheRoll() {
        String roll = "6A-701";

        // Another school already owns a login on the bare roll number.
        com.concept.user.User squatter = new com.concept.user.User();
        squatter.setId(UUID.randomUUID());
        squatter.setTenantId(UUID.randomUUID());
        squatter.setAcademicYearId(UUID.randomUUID());
        squatter.setEmail(roll);
        squatter.setFullName("Other School Student");
        squatter.setRole(com.concept.user.UserRole.STUDENT);
        squatter.setActive(true);
        squatter.setPasswordHash("x");
        userRepository.saveAndFlush(squatter);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName("Nisha");
        student.setLastName("Rao");
        student.setRollNumber(roll);
        student.setSchoolClass(schoolClass);
        student.setClassSection(classSection);
        studentRepository.saveAndFlush(student);

        String creds = studentAdminService.resetStudentLogin(student.getId(), tenantId, NO_AUTH);

        assertTrue(creds.contains("nisha" + roll.toLowerCase() + "@" + subdomain),
                "reset should issue a school-qualified username, was: " + creds);
    }

    /**
     * Guardian logins had the identical defect with phone numbers: User.email is
     * globally unique, so the first school to register a number claimed it for
     * the whole platform and later schools silently got no guardian login --
     * the caller checked existsByEmail and just skipped provisioning.
     */
    @Test
    public void addStudent_guardianGetsALogin_evenWhenAnotherSchoolHasThePhoneNumber() {
        String phone = "+91 90000 12345";

        com.concept.user.User squatter = new com.concept.user.User();
        squatter.setId(UUID.randomUUID());
        squatter.setTenantId(UUID.randomUUID());
        squatter.setAcademicYearId(UUID.randomUUID());
        squatter.setEmail(phone);
        squatter.setFullName("Other School Guardian");
        squatter.setRole(com.concept.user.UserRole.PARENT);
        squatter.setActive(true);
        squatter.setPasswordHash("x");
        userRepository.saveAndFlush(squatter);

        String roll = "R-" + UUID.randomUUID().toString().substring(0, 8);
        String creds = studentAdminService.addStudent("Aarav", "Mehta", roll, schoolClass.getId(),
                null, null, "Gurmeet", "Singh", phone, tenantId, yearId, NO_AUTH);

        assertNotNull(creds, "credentials should be relayed back");
        assertTrue(creds.contains("Guardian login"),
                "the guardian must still get a login when another school holds that phone number, was: " + creds);
        assertTrue(creds.contains("gurmeet919000012345@" + subdomain),
                "guardian username should be school-qualified, was: " + creds);
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
