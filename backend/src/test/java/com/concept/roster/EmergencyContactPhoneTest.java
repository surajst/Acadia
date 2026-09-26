package com.concept.roster;

import com.concept.roster.app.ChildDetails;
import com.concept.roster.app.StudentAdminService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The emergency contact number is the one a school rings when something has
 * happened to a child, and it accepted any text at all: {@code blankToNull} and
 * nothing else. So "call mum" was as acceptable as a phone number, and nobody
 * would find out until the day it was needed.
 *
 * <p>It now takes the same rule as the guardian number -- {@link
 * com.concept.roster.app.PhoneNumbers}, the one lifted out of the CSV importer so
 * the form and the spreadsheet could not disagree.
 *
 * <p>Driven through {@link StudentAdminService}, not the profile form: a rule the
 * form keeps and the service does not is no rule, which is the shape of fault this
 * codebase has shipped more than once.
 *
 * <p>The awkward half is {@link #anUnrelatedEditToARecordWithABadNumberStillSaves()}.
 * The profile form posts every field it holds, so a check on every save would
 * refuse an edit to a child's allergies because their emergency number was typed
 * wrong two years ago -- the rule becoming the problem. It fires on change only.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class EmergencyContactPhoneTest {

    @Autowired private StudentAdminService studentAdminService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private Student aarav;
    private Authentication admin;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("ecp-" + suffix);
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.now().minusMonths(3));
        year.setEndDate(LocalDate.now().plusMonths(9));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        sixA = new ClassSection();
        sixA.setId(UUID.randomUUID());
        sixA.setTenantId(tenantId);
        sixA.setAcademicYearId(yearId);
        sixA.setGradeName("Grade 6");
        sixA.setSectionName("A");
        sixA = classSectionRepository.saveAndFlush(sixA);

        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setTenantId(tenantId);
        adminUser.setAcademicYearId(yearId);
        adminUser.setEmail("admin-" + suffix + "@example.com");
        adminUser.setPasswordHash("irrelevant");
        adminUser.setFullName("School Admin");
        adminUser.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(adminUser);
        admin = new UsernamePasswordAuthenticationToken(adminUser.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        aarav = new Student();
        aarav.setId(UUID.randomUUID());
        aarav.setTenantId(tenantId);
        aarav.setAcademicYearId(yearId);
        aarav.setFirstName("Aarav");
        aarav.setLastName("Verma");
        aarav.setRollNumber("6A-01");
        aarav.setClassSection(sixA);
        aarav = studentRepository.saveAndFlush(aarav);
    }

    /** What the profile form does: posts the whole record back. */
    private void save(String emergencyPhone) {
        studentAdminService.updateStudent(aarav.getId(), tenantId, yearId,
                "Aarav", "Verma", "6A-01", null,
                null, null, null,
                new ChildDetails(null, "None", "Meera Verma", emergencyPhone),
                admin);
    }

    private String storedNumber() {
        return studentRepository.findByIdAndTenantId(aarav.getId(), tenantId)
                .orElseThrow().getEmergencyContactPhone();
    }

    // ── The rule ─────────────────────────────────────────────────────────────

    @Test
    void aNumberASchoolCanRingIsAccepted() {
        assertDoesNotThrow(() -> save("+91 98765 43210"));
        assertEquals("+91 98765 43210", storedNumber());
    }

    @Test
    void somethingThatIsNotANumberAtAllIsRefused() {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> save("call mum"));

        assertTrue(refused.getMessage().contains("emergency contact"),
                "the refusal has to say which field: " + refused.getMessage());
    }

    @Test
    void tooFewDigitsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> save("98765"));
    }

    /** Seven digits is the floor the importer has always used. */
    @Test
    void sevenDigitsIsEnough() {
        assertDoesNotThrow(() -> save("9876543"));
    }

    @Test
    void punctuationWithNoNumberInItIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> save("((( )))-"));
    }

    // ── Blank stays optional ─────────────────────────────────────────────────

    @Test
    void blankIsStillAllowed() {
        assertDoesNotThrow(() -> save("   "));
        assertNull(storedNumber(), "blank is stored as null, as it always was");
    }

    @Test
    void absentIsStillAllowed() {
        assertDoesNotThrow(() -> save(null));
        assertNull(storedNumber());
    }

    // ── Records that already hold a bad number ───────────────────────────────

    /**
     * The case that decides where the check goes. A record written before the rule
     * existed keeps its unusable number; editing anything else about that child
     * must still work, or the rule blocks unrelated work on the children whose
     * records are already worst.
     */
    @Test
    void anUnrelatedEditToARecordWithABadNumberStillSaves() {
        // Straight to the repository, standing in for a row written before the rule.
        aarav.setEmergencyContactPhone("call mum");
        studentRepository.saveAndFlush(aarav);

        assertDoesNotThrow(() -> studentAdminService.updateStudent(aarav.getId(), tenantId, yearId,
                "Aarav", "Verma", "6A-02", null,
                null, null, null,
                new ChildDetails(null, "Peanut allergy", "Meera Verma", "call mum"),
                admin));

        assertEquals("Peanut allergy", studentRepository.findByIdAndTenantId(aarav.getId(), tenantId)
                .orElseThrow().getMedicalNotes(), "the edit the admin actually came to make");
    }

    /** But touching that number means fixing it. */
    @Test
    void changingABadNumberToAnotherBadOneIsRefused() {
        aarav.setEmergencyContactPhone("call mum");
        studentRepository.saveAndFlush(aarav);

        assertThrows(IllegalArgumentException.class, () -> save("call dad"));
    }

    @Test
    void changingABadNumberToARealOneWorks() {
        aarav.setEmergencyContactPhone("call mum");
        studentRepository.saveAndFlush(aarav);

        assertDoesNotThrow(() -> save("+91 98765 43210"));
        assertEquals("+91 98765 43210", storedNumber());
    }

    /** And clearing one is allowed, since the field is optional. */
    @Test
    void clearingABadNumberIsAllowed() {
        aarav.setEmergencyContactPhone("call mum");
        studentRepository.saveAndFlush(aarav);

        assertDoesNotThrow(() -> save(null));
        assertNull(storedNumber());
    }
}
