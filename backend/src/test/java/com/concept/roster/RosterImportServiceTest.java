package com.concept.roster;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;

import com.concept.roster.app.RosterImportService;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the bulk-import logic directly through {@link RosterImportService} —
 * no HTTP request, session, or Spring Security — which is the whole reason the
 * logic was pulled out of the controller.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class RosterImportServiceTest {

    @Autowired
    private RosterImportService rosterImportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    /**
     * A "current user" backed by a freshly-persisted tenant + academic year, so
     * the FK constraints on students/class_sections/parents are satisfied while
     * each test still runs in its own isolated tenant.
     */
    private User admin() {
        UUID tenantId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Test Tenant " + tenantId);
        tenant.setSubdomain("t-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(java.time.Instant.now());
        tenantRepository.save(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(java.time.LocalDate.of(2026, 4, 1));
        year.setEndDate(java.time.LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.save(year);

        User u = new User();
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        return u;
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "roster.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void previewClassifiesRowsWithoutWriting() throws Exception {
        User admin = admin();
        String content = String.join("\n",
                "FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone",
                "Aarav,Mehta,R1,Grade 5,A,Rohan Mehta,+91 9812345670",
                "Dup,Row,R1,Grade 5,A,Someone,+91 9812345672",   // intra-file duplicate roll -> skip
                ",NoName,R2,Grade 6,C,Parent X,+91 9812345673",  // missing first name -> error
                "Bad,Phone,R3,Grade 6,C,Parent Y,abc");          // bad phone -> error

        RosterImportService.StudentPreview preview = rosterImportService.previewStudents(csv(content).getInputStream(), "roster.csv", admin);

        assertEquals(1, preview.willCreate());
        assertEquals(1, preview.willSkip());
        assertEquals(2, preview.willFail());
        assertTrue(preview.canCommit());
        // Dry run: no student login was provisioned.
        assertFalse(userRepository.existsByEmail(studentLogin(admin, "Aarav", "R1")));
    }

    /** The school-qualified username the import should mint, e.g. "aaravr1@t-1a2b3c4d". */
    private String studentLogin(User admin, String firstName, String rollNumber) {
        return firstName.toLowerCase() + rollNumber.toLowerCase() + "@" + subdomain(admin);
    }

    /** The guardian equivalent: first name + digits of the phone, school-qualified. */
    /**
     * A guardian's login is their own name, school-qualified. It used to carry
     * their full phone number -- "rohan9812345670@school" -- which published a
     * parent's mobile as their username on a credentials sheet.
     */
    private String guardianLogin(User admin, String firstName, String lastName) {
        return firstName.toLowerCase() + "." + lastName.toLowerCase() + "@" + subdomain(admin);
    }

    private String subdomain(User admin) {
        return tenantRepository.findById(admin.getTenantId()).orElseThrow().getSubdomain();
    }

    @Test
    public void commitCreatesStudentsAndLogins() throws Exception {
        User admin = admin();
        String content = String.join("\n",
                "FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone",
                "Aarav,Mehta,R1,Grade 5,A,Rohan Mehta,+91 9812345670",
                "Isha,Verma,R2,Grade 5,B,Sunil Verma,+91 9812345671");

        RosterImportService.StudentPreview preview = rosterImportService.previewStudents(csv(content).getInputStream(), "roster.csv", admin);
        RosterImportService.ImportResult result = rosterImportService.commitStudents(preview.rows(), admin);

        assertEquals(2, result.created());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        // Student and parent logins were provisioned, under the school-qualified
        // usernames. These assertions used to read existsByEmail("R1") and
        // existsByEmail("+91 9812345670") -- the raw roll number and phone -- which
        // pinned the very namespace collision that stopped the second school to
        // import a given roll number from getting any login at all.
        assertTrue(userRepository.existsByEmail(studentLogin(admin, "Aarav", "R1")));
        assertTrue(userRepository.existsByEmail(guardianLogin(admin, "Rohan", "Mehta")));
        // And emphatically not under the bare values, nor under the phone number
        // in any form -- that was the username until it was taken out of it.
        assertFalse(userRepository.existsByEmail("R1"));
        assertFalse(userRepository.existsByEmail("+91 9812345670"));
        assertFalse(userRepository.existsByEmail("rohan9812345670@" + subdomain(admin)));
    }

    /**
     * Two schools importing the same roll number and the same parent phone must
     * each end up with working logins.
     *
     * <p>They did not. User.email carries a global unique constraint, and the
     * import used the bare roll number and bare phone as the username, so the
     * first school to import "R1" claimed it platform-wide; every later school
     * fell through the existsByEmail check and silently provisioned nothing --
     * while the row still reported "Created". A child who could not sign in, and
     * nothing anywhere saying why.
     */
    @Test
    public void twoSchoolsCanImportTheSameRollNumberAndPhone() throws Exception {
        String row = "Aarav,Mehta,R1,Grade 5,A,Rohan Mehta,+91 9812345670";
        String content = String.join("\n",
                "FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone", row);

        User first = admin();
        rosterImportService.commitStudents(
                rosterImportService.previewStudents(csv(content).getInputStream(), "roster.csv", first).rows(), first);

        User second = admin();
        RosterImportService.ImportResult result = rosterImportService.commitStudents(
                rosterImportService.previewStudents(csv(content).getInputStream(), "roster.csv", second).rows(), second);

        assertEquals(1, result.created(), "the second school's row should import");

        // Both schools have their own working student and guardian logins.
        assertTrue(userRepository.existsByEmail(studentLogin(first, "Aarav", "R1")),
                "first school's pupil must keep their login");
        assertTrue(userRepository.existsByEmail(studentLogin(second, "Aarav", "R1")),
                "second school's pupil must get a login of their own");
        assertTrue(userRepository.existsByEmail(guardianLogin(first, "Rohan", "Mehta")));
        assertTrue(userRepository.existsByEmail(guardianLogin(second, "Rohan", "Mehta")));

        // The row must say what was provisioned, so a blank credentials cell can
        // never again be the only sign that a login was skipped.
        String detail = result.outcomes().get(0).get("detail");
        assertTrue(detail.contains(studentLogin(second, "Aarav", "R1")),
                "the outcome should hand the admin the credentials it created; got: " + detail);
    }

    @Test
    public void commitSkipsRollNumberThatAlreadyExists() throws Exception {
        User admin = admin();
        String content = String.join("\n",
                "FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone",
                "Aarav,Mehta,R1,Grade 5,A,Rohan Mehta,+91 9812345670");

        // First import creates R1.
        rosterImportService.commitStudents(rosterImportService.previewStudents(csv(content).getInputStream(), "roster.csv", admin).rows(), admin);
        // Second import of the same roll number is skipped, not duplicated.
        RosterImportService.ImportResult second =
                rosterImportService.commitStudents(rosterImportService.previewStudents(csv(content).getInputStream(), "roster.csv", admin).rows(), admin);

        assertEquals(0, second.created());
        assertEquals(1, second.skipped());
    }

    @Test
    public void staffImportCreatesValidRowsAndRejectsBadRoles() throws Exception {
        User admin = admin();
        String content = String.join("\n",
                "FullName,Email,Role",
                "Anita Rao,anita." + UUID.randomUUID() + "@school.edu,TEACHER",
                "Bad Role,badrole." + UUID.randomUUID() + "@school.edu,WIZARD");

        RosterImportService.ImportResult result =
                rosterImportService.importStaff(csv(content).getInputStream(), "roster.csv", admin.getTenantId(), admin.getAcademicYearId());

        assertEquals(1, result.created());
        assertEquals(1, result.failed());
    }
}
