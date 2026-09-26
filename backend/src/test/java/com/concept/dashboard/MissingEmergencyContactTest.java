package com.concept.dashboard;

import com.concept.assignment.app.SubjectAssignmentService;
import com.concept.dashboard.app.DashboardService;
import com.concept.dashboard.app.RosterDashboardView;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thirty of the pilot school's fifty-two children had no emergency contact
 * number, and nothing on any screen said so. The field is optional and stays
 * optional -- a school cannot always get a number the day a child is enrolled,
 * and refusing the enrolment over it would be worse. What was missing was anybody
 * knowing.
 *
 * <p>So the dashboard counts them and the count links to the list. The two have to
 * agree, and both have to be scoped to what the caller may see: a teacher's figure
 * must match the children a teacher can open, or the number leads to a roster that
 * is not theirs.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class MissingEmergencyContactTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private SubjectAssignmentService assignmentService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sixA;
    private ClassSection sixB;
    private User admin;
    private User priya;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("mec-" + suffix);
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

        sixA = section("Grade 6", "A");
        sixB = section("Grade 6", "B");

        admin = person("admin-" + suffix + "@example.com", UserRole.ADMIN);
        priya = person("priya-" + suffix + "@example.com", UserRole.TEACHER);
        assignmentService.assignSubject(priya.getId(), sixA.getId(), "Mathematics", true, tenantId);
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

    private User person(String email, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(email);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private Student child(String first, ClassSection in, String emergencyPhone) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName("Verma");
        s.setClassSection(in);
        s.setEmergencyContactPhone(emergencyPhone);
        return studentRepository.saveAndFlush(s);
    }

    private RosterDashboardView asAdmin(boolean onlyMissing) {
        return dashboardService.buildRosterDashboard(tenantId, admin.getEmail(), null, null, null,
                onlyMissing, 0, 50, false);
    }

    private RosterDashboardView asTeacher(boolean onlyMissing) {
        return dashboardService.buildRosterDashboard(tenantId, priya.getEmail(), null, null, null,
                onlyMissing, 0, 50, false);
    }

    // ── The count ────────────────────────────────────────────────────────────

    @Test
    void theCountIsTheChildrenWithNoNumber() {
        child("Aarav", sixA, "+91 98765 43210");
        child("Diya", sixA, null);
        child("Rohan", sixB, null);

        assertEquals(2, asAdmin(false).missingEmergencyContact());
    }

    /**
     * Blank is missing. StudentAdminService stores "" as null, but the importer and
     * rows written before that rule can hold one -- and an empty string is not a
     * number anyone can ring.
     */
    @Test
    void anEmptyStringCountsAsMissing() {
        child("Aarav", sixA, "   ");

        assertEquals(1, asAdmin(false).missingEmergencyContact());
    }

    @Test
    void aSchoolWithEveryNumberOnFileCountsNone() {
        child("Aarav", sixA, "+91 98765 43210");
        child("Diya", sixB, "9876543210");

        assertEquals(0, asAdmin(false).missingEmergencyContact());
    }

    // ── The list the count links to ──────────────────────────────────────────

    @Test
    void theFilteredListIsExactlyThoseChildren() {
        child("Aarav", sixA, "+91 98765 43210");
        child("Diya", sixA, null);
        child("Rohan", sixB, null);

        RosterDashboardView filtered = asAdmin(true);

        assertEquals(2, filtered.totalRosterItems());
        List<String> names = filtered.roster().stream().map(r -> r.firstName()).sorted().toList();
        assertEquals(List.of("Diya", "Rohan"), names);
    }

    /**
     * The count and the list have to be the same answer. They come from two
     * different queries, so nothing but a test keeps them honest.
     */
    @Test
    void theCountAndTheListAgree() {
        child("Aarav", sixA, "+91 98765 43210");
        child("Diya", sixA, null);
        child("Rohan", sixB, "");
        child("Meera", sixB, null);

        RosterDashboardView unfiltered = asAdmin(false);
        RosterDashboardView filtered = asAdmin(true);

        assertTrue(unfiltered.missingEmergencyContact() > 0, "nothing is being tested at zero");
        assertEquals(unfiltered.missingEmergencyContact(), filtered.totalRosterItems());
    }

    @Test
    void theUnfilteredRosterStillShowsEverybody() {
        child("Aarav", sixA, "+91 98765 43210");
        child("Diya", sixA, null);

        assertEquals(2, asAdmin(false).totalRosterItems(),
                "the filter must be opt-in; the ordinary roster is unchanged");
    }

    // ── Scope ────────────────────────────────────────────────────────────────

    /**
     * A teacher's figure must match the children a teacher can open. A count that
     * spans the school, linking to a list that does not, is a leak wearing a
     * to-do list's clothes -- and it is the same fallback that once had every
     * teacher reading the whole school's roster.
     */
    @Test
    void aTeacherSeesOnlyTheirOwnSectionsBothWays() {
        child("Diya", sixA, null);     // hers
        child("Rohan", sixB, null);    // not hers
        child("Meera", sixB, null);    // not hers

        RosterDashboardView hers = asTeacher(false);
        assertEquals(1, hers.missingEmergencyContact(),
                "a teacher's count is about her own children");

        RosterDashboardView herList = asTeacher(true);
        assertEquals(1, herList.totalRosterItems());
        assertEquals("Diya", herList.roster().get(0).firstName());
    }

    @Test
    void aTeacherWithNoSectionsSeesNothingRatherThanEverything() {
        User newStarter = person("new-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                UserRole.TEACHER);
        child("Diya", sixA, null);
        child("Rohan", sixB, null);

        RosterDashboardView theirs = dashboardService.buildRosterDashboard(
                tenantId, newStarter.getEmail(), null, null, null, true, 0, 50, false);

        assertEquals(0, theirs.missingEmergencyContact());
        assertTrue(theirs.roster().isEmpty(),
                "no assignments must mean no children, not the whole school");
    }

    // ── Composing with the search box ────────────────────────────────────────

    /**
     * The search box stays usable while the filter is on, so the two have to
     * combine. If they did not, typing a name would silently widen the list back
     * to children who do have a number.
     */
    @Test
    void aNameSearchNarrowsWithinTheFilterRatherThanReplacingIt() {
        child("Aarav", sixA, null);
        child("Diya", sixA, null);
        child("Aaravi", sixB, "+91 98765 43210");   // matches the name, has a number

        RosterDashboardView filtered = dashboardService.buildRosterDashboard(
                tenantId, admin.getEmail(), null, "Aarav", null, true, 0, 50, false);

        assertEquals(1, filtered.totalRosterItems(),
                "the child with a number matches the name but must not be listed");
        assertEquals("Aarav", filtered.roster().get(0).firstName());
        assertFalse(filtered.roster().stream().anyMatch(r -> "Aaravi".equals(r.firstName())));
    }
}
