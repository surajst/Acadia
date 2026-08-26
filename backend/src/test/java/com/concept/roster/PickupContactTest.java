package com.concept.roster;

import com.concept.roster.app.PickupContactService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.PickupContactRepository;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The list of people allowed to collect a child.
 *
 * <p>Treated as a safety record rather than a contact list: it is tenant-scoped
 * like everything else, it is audited on both add and revoke, and a revoked
 * name has to actually disappear. A school asked "who authorised this person"
 * after an incident should not have to take anyone's word for the answer.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class PickupContactTest {

    @Autowired private PickupContactService pickupContactService;
    @Autowired private PickupContactRepository pickupContactRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantA;
    private UUID tenantB;
    private Student childA;
    private Student childB;
    private Authentication adminA;

    private UUID makeTenant(String label) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(label);
        tenant.setSubdomain(label + "-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        return tenantRepository.saveAndFlush(tenant).getId();
    }

    private Student makeChild(UUID tenantId, String firstName) {
        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        UUID yearId = academicYearRepository.saveAndFlush(year).getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Nursery");
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName(firstName);
        student.setLastName("Tot");
        student.setClassSection(section);
        return studentRepository.saveAndFlush(student);
    }

    private Authentication makeAdmin(UUID tenantId, UUID yearId, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("irrelevant");
        user.setFullName(email);
        user.setRole(UserRole.ADMIN);
        user.setTenantId(tenantId);
        user.setAcademicYearId(yearId);
        userRepository.saveAndFlush(user);
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @BeforeEach
    public void setup() {
        tenantA = makeTenant("school-a");
        tenantB = makeTenant("school-b");
        childA = makeChild(tenantA, "Aarav");
        childB = makeChild(tenantB, "Bhavya");
        adminA = makeAdmin(tenantA, childA.getAcademicYearId(), "admin@school-a.test");
    }

    @Test
    public void anAuthorisedPersonIsRecordedAndListed() {
        pickupContactService.add(childA.getId(), "Anita Rao", "Grandmother", "+91 90000 00001",
                tenantA, adminA);

        List<PickupContactService.Row> list = pickupContactService.forStudent(childA.getId(), tenantA);
        assertEquals(1, list.size());
        assertEquals("Anita Rao", list.get(0).name());
        assertEquals("Grandmother", list.get(0).relationship());
    }

    @Test
    public void revokingRemovesThemFromTheList() {
        pickupContactService.add(childA.getId(), "Anita Rao", "Grandmother", null, tenantA, adminA);
        UUID contactId = pickupContactService.forStudent(childA.getId(), tenantA).get(0).id();

        pickupContactService.remove(contactId, tenantA, adminA);

        assertTrue(pickupContactService.forStudent(childA.getId(), tenantA).isEmpty(),
                "a revoked authorisation must actually disappear");
    }

    @Test
    public void anEntryWithNoNameIsRefused() {
        // A list entry nobody can identify is worse than no entry: it looks
        // like an authorisation while authorising nobody in particular.
        assertThrows(IllegalArgumentException.class,
                () -> pickupContactService.add(childA.getId(), "  ", "Driver", "+91", tenantA, adminA));
        assertTrue(pickupContactService.forStudent(childA.getId(), tenantA).isEmpty());
    }

    @Test
    public void oneSchoolCannotReadAnothersList() {
        pickupContactService.add(childB.getId(), "Someone Else", "Uncle", null, tenantB,
                makeAdmin(tenantB, childB.getAcademicYearId(), "admin@school-b.test"));

        assertTrue(pickupContactService.forStudent(childB.getId(), tenantA).isEmpty(),
                "school A must not see who may collect school B's child");
    }

    @Test
    public void oneSchoolCannotAuthoriseSomeoneForAnothersChild() {
        assertThrows(IllegalArgumentException.class,
                () -> pickupContactService.add(childB.getId(), "Intruder", null, null, tenantA, adminA),
                "a student id from another school must not resolve");
        assertTrue(pickupContactRepository.findByStudentIdAndTenantIdOrderByNameAsc(
                childB.getId(), tenantB).isEmpty());
    }

    @Test
    public void oneSchoolCannotRevokeAnothersEntry() {
        pickupContactService.add(childB.getId(), "Legitimate Aunt", null, null, tenantB,
                makeAdmin(tenantB, childB.getAcademicYearId(), "admin2@school-b.test"));
        UUID contactId = pickupContactService.forStudent(childB.getId(), tenantB).get(0).id();

        assertThrows(IllegalArgumentException.class,
                () -> pickupContactService.remove(contactId, tenantA, adminA));
        assertEquals(1, pickupContactService.forStudent(childB.getId(), tenantB).size(),
                "the entry must survive a cross-tenant revoke attempt");
    }

    @Test
    public void theSafetyFieldsRoundTripOnTheStudent() {
        childA.setDateOfBirth(LocalDate.of(2022, 3, 14));
        childA.setMedicalNotes("Peanut allergy — EpiPen in the office");
        childA.setEmergencyContactName("Meera Rao");
        childA.setEmergencyContactPhone("+91 90000 00002");
        studentRepository.saveAndFlush(childA);

        Student reloaded = studentRepository.findByIdAndTenantId(childA.getId(), tenantA).orElseThrow();
        assertEquals(LocalDate.of(2022, 3, 14), reloaded.getDateOfBirth());
        assertTrue(reloaded.getMedicalNotes().contains("Peanut"));
        assertEquals("Meera Rao", reloaded.getEmergencyContactName());
    }
}
