package com.concept.timetable;

import com.concept.timetable.app.TimetableEntryRequest;
import com.concept.timetable.app.TimetableException;
import com.concept.timetable.app.TimetableService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timetable accepted everything: the same teacher in two rooms at once, a
 * class given two subjects in one period, and a period running from 10:00 to
 * 09:00. One test per rule, each written from the reported repro.
 *
 * <p>The messages are asserted as well as the refusals. "Invalid entry" would
 * leave an admin staring at a grid of forty periods with no idea which one is
 * the problem, so each message has to name the teacher or class and the time.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class TimetableValidationTest {

    @Autowired private TimetableService timetableService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.concept.assignment.app.SubjectAssignmentService assignmentService;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection sectionA;
    private ClassSection sectionB;
    private User priya;
    private Authentication admin;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("tt-" + UUID.randomUUID());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantId = tenantRepository.saveAndFlush(tenant).getId();

        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        yearId = academicYearRepository.saveAndFlush(year).getId();

        sectionA = section("Grade 6", "A");
        sectionB = section("Grade 6", "B");

        priya = user("priya.teacher@example.com", "Priya Demo", UserRole.TEACHER);
        // The service resolves the tenant from the caller, so it has to be a real row.
        User adminUser = user("suraj10@gmail.com", "Suraj Demo", UserRole.ADMIN);
        admin = new UsernamePasswordAuthenticationToken(adminUser.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
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

    private User user(String email, String name, UserRole role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setAcademicYearId(yearId);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setFullName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private TimetableEntryRequest slot(ClassSection section, User teacher, String day,
                                       int period, String start, String end, String subject) {
        TimetableEntryRequest r = new TimetableEntryRequest();
        r.setClassSectionId(section.getId());
        r.setTeacherId(teacher.getId());
        r.setDayOfWeek(day);
        r.setPeriodNumber(period);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setSubjectName(subject);
        return r;
    }

    private Map<String, Object> create(TimetableEntryRequest r) {
        return timetableService.adminCreate(r, admin);
    }

    /** The same teacher in 6A and 6B on MON period 1, 08:00 to 08:45. */
    @Test
    void aTeacherCannotBeInTwoClassesAtOnce() {
        create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));

        TimetableException e = assertThrows(TimetableException.class,
                () -> create(slot(sectionB, priya, "MON", 1, "08:00", "08:45", "Mathematics")));

        assertTrue(e.getMessage().contains("Priya Demo"), "name the teacher: " + e.getMessage());
        assertTrue(e.getMessage().contains("Grade 6 A"), "name where they already are: " + e.getMessage());
        assertTrue(e.getMessage().contains("08:00"), "name the time: " + e.getMessage());
    }

    /** Two subjects for 6A on MON period 1. */
    @Test
    void aClassCannotBeTaughtTwoSubjectsInOnePeriod() {
        create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));
        User vikram = user("vikram@example.com", "Vikram Rao", UserRole.TEACHER);

        TimetableException e = assertThrows(TimetableException.class,
                () -> create(slot(sectionA, vikram, "MON", 1, "08:00", "08:45", "Science")));

        assertTrue(e.getMessage().contains("Grade 6 A"), e.getMessage());
        assertTrue(e.getMessage().contains("Mathematics"), "say what is already there: " + e.getMessage());
    }

    /** The same clash typed under a different period number is still a clash. */
    @Test
    void aClassCannotHaveOverlappingTimesUnderADifferentPeriodNumber() {
        create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));
        User vikram = user("vikram2@example.com", "Vikram Rao", UserRole.TEACHER);

        assertThrows(TimetableException.class,
                () -> create(slot(sectionA, vikram, "MON", 7, "08:30", "09:15", "Science")));
    }

    /** A period from 10:00 to 09:00. */
    @Test
    void aPeriodCannotEndBeforeItStarts() {
        TimetableException e = assertThrows(TimetableException.class,
                () -> create(slot(sectionA, priya, "MON", 1, "10:00", "09:00", "Mathematics")));
        assertTrue(e.getMessage().contains("10:00") && e.getMessage().contains("09:00"), e.getMessage());
    }

    @Test
    void aPeriodCannotHaveZeroLength() {
        assertThrows(TimetableException.class,
                () -> create(slot(sectionA, priya, "MON", 1, "09:00", "09:00", "Mathematics")));
    }

    @Test
    void aMalformedTimeIsRefusedRatherThanStored() {
        assertThrows(TimetableException.class,
                () -> create(slot(sectionA, priya, "MON", 1, "half past nine", "10:00", "Mathematics")));
    }

    /**
     * The rules must not block an ordinary timetable. Back-to-back periods
     * touch at 08:45, which is not an overlap.
     */
    @Test
    void consecutivePeriodsAreFine() {
        create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));
        assertDoesNotThrow(() -> create(slot(sectionA, priya, "MON", 2, "08:45", "09:30", "Science")));
    }

    /** The same slot on a different day is not a clash either. */
    @Test
    void theSameSlotOnAnotherDayIsFine() {
        create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));
        assertDoesNotThrow(() -> create(slot(sectionA, priya, "TUE", 1, "08:00", "08:45", "Mathematics")));
    }

    /**
     * A teacher given a section they are not assigned to. This one warns rather
     * than refuses: schools do use cover teachers, and an admin building a first
     * timetable has usually not filled in Teacher Assignments yet. Refusing
     * would block the ordinary path, which is the mistake the fee approval gate
     * made.
     *
     * <p>With no assignments recorded for the section at all, silence is not a
     * signal, so nothing is said.
     */
    @Test
    void anUnassignedTeacherIsAcceptedQuietlyWhenNothingIsConfigured() {
        Map<String, Object> saved = create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));
        assertFalse(saved.containsKey("warnings"),
                "with no assignments on file there is nothing to contradict");
    }

    /** Nudging an entry must not clash with where it already is. */
    @Test
    void movingAnEntrySlightlyDoesNotClashWithItself() {
        Map<String, Object> saved = create(slot(sectionA, priya, "MON", 1, "08:00", "08:45", "Mathematics"));
        UUID id = UUID.fromString(String.valueOf(saved.get("id")));

        TimetableEntryRequest move = new TimetableEntryRequest();
        move.setStartTime("08:05");
        move.setEndTime("08:50");

        assertDoesNotThrow(() -> timetableService.adminUpdate(id, move, admin));
    }

    /**
     * The reported case: Neha is assigned 6-B Science and was accepted onto
     * 7-A English without a word.
     *
     * <p>The first version of this check keyed on the section's assignments,
     * so a section with none configured said nothing at all -- which is
     * exactly the section she was wrongly put on. What the section has
     * configured says nothing about whether this teacher belongs in it, so it
     * keys on the teacher now.
     */
    @Test
    void aTeacherOnASectionTheyAreNotAssignedToIsFlagged() {
        ClassSection sectionC = section("Grade 7", "A");
        User neha = user("neha.teacher@example.com", "Neha Test", UserRole.TEACHER);
        assignmentService.assignSubject(neha.getId(), sectionB.getId(), "Science", true, tenantId);

        Map<String, Object> saved = create(slot(sectionC, neha, "MON", 1, "08:00", "08:45", "English"));

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) saved.get("warnings");
        assertNotNull(warnings, "this is the case the check exists for");
        assertTrue(warnings.get(0).contains("Neha Test"), warnings.get(0));
        assertTrue(warnings.get(0).contains("Grade 7 A"), warnings.get(0));
        assertTrue(warnings.get(0).contains("Grade 6 B"), "say where they are assigned: " + warnings.get(0));
    }

    /** Right class, wrong subject, is worth saying too. */
    @Test
    void aTeacherAssignedForAnotherSubjectIsFlagged() {
        User neha = user("neha2@example.com", "Neha Test", UserRole.TEACHER);
        assignmentService.assignSubject(neha.getId(), sectionA.getId(), "Science", false, tenantId);

        Map<String, Object> saved = create(slot(sectionA, neha, "MON", 1, "08:00", "08:45", "English"));

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) saved.get("warnings");
        assertNotNull(warnings, "a Science teacher down for English is usually a mistyped row");
        assertTrue(warnings.get(0).contains("Science"), warnings.get(0));
    }

    /** The assignment they actually hold must pass without comment. */
    @Test
    void aTeacherOnTheirOwnAssignedSubjectIsNotFlagged() {
        User neha = user("neha3@example.com", "Neha Test", UserRole.TEACHER);
        assignmentService.assignSubject(neha.getId(), sectionA.getId(), "Science", false, tenantId);

        Map<String, Object> saved = create(slot(sectionA, neha, "TUE", 1, "08:00", "08:45", "Science"));
        assertFalse(saved.containsKey("warnings"), "this is the ordinary, correct case");
    }
}
