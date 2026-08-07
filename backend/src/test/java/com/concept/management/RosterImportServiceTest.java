package com.concept.management;

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

        RosterImportService.StudentPreview preview = rosterImportService.previewStudents(csv(content), admin);

        assertEquals(1, preview.willCreate());
        assertEquals(1, preview.willSkip());
        assertEquals(2, preview.willFail());
        assertTrue(preview.canCommit());
        // Dry run: no student login was provisioned.
        assertFalse(userRepository.existsByEmail("R1"));
    }

    @Test
    public void commitCreatesStudentsAndLogins() throws Exception {
        User admin = admin();
        String content = String.join("\n",
                "FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone",
                "Aarav,Mehta,R1,Grade 5,A,Rohan Mehta,+91 9812345670",
                "Isha,Verma,R2,Grade 5,B,Sunil Verma,+91 9812345671");

        RosterImportService.StudentPreview preview = rosterImportService.previewStudents(csv(content), admin);
        RosterImportService.ImportResult result = rosterImportService.commitStudents(preview.rows(), admin);

        assertEquals(2, result.created());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        // Student and parent logins were provisioned.
        assertTrue(userRepository.existsByEmail("R1"));
        assertTrue(userRepository.existsByEmail("+91 9812345670"));
    }

    @Test
    public void commitSkipsRollNumberThatAlreadyExists() throws Exception {
        User admin = admin();
        String content = String.join("\n",
                "FirstName,LastName,RollNumber,Grade,Section,ParentName,ParentPhone",
                "Aarav,Mehta,R1,Grade 5,A,Rohan Mehta,+91 9812345670");

        // First import creates R1.
        rosterImportService.commitStudents(rosterImportService.previewStudents(csv(content), admin).rows(), admin);
        // Second import of the same roll number is skipped, not duplicated.
        RosterImportService.ImportResult second =
                rosterImportService.commitStudents(rosterImportService.previewStudents(csv(content), admin).rows(), admin);

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
                rosterImportService.importStaff(csv(content), admin.getTenantId(), admin.getAcademicYearId());

        assertEquals(1, result.created());
        assertEquals(1, result.failed());
    }
}
