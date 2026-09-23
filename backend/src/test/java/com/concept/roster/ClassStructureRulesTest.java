package com.concept.roster;

import com.concept.roster.app.ClassStructureService;
import com.concept.roster.app.PhoneNumbers;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import com.concept.timetable.data.TimetableEntry;
import com.concept.timetable.data.TimetableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Onboarding accepted "Grade 6 / A" twice, and the bin icon deleted a section
 * on one click. Two rows for one class split the roster, the timetable and
 * every fee figure between them in a way nobody can reconcile later.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class ClassStructureRulesTest {

    @Autowired private ClassStructureService classStructureService;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TimetableRepository timetableRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("cs-" + UUID.randomUUID());
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
    }

    private void add(String grade, String section) {
        classStructureService.addSection(tenantId, yearId, grade, section, "101", 30, null);
    }

    @Test
    void theSameSectionCannotBeAddedTwice() {
        add("Grade 6", "A");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> add("Grade 6", "A"));
        assertTrue(e.getMessage().contains("Grade 6 A"), e.getMessage());
        assertEquals(1, classStructureService.listSections(tenantId).size());
    }

    /** Case and stray spaces do not make it a different class. */
    @Test
    void aDuplicateIsStillADuplicateInAnotherCase() {
        add("Grade 6", "A");
        assertThrows(IllegalArgumentException.class, () -> add("grade 6", " a "));
        assertEquals(1, classStructureService.listSections(tenantId).size());
    }

    @Test
    void adifferentSectionOfTheSameGradeIsFine() {
        add("Grade 6", "A");
        add("Grade 6", "B");
        assertEquals(2, classStructureService.listSections(tenantId).size());
    }

    /** Another school naming its section the same way is not a clash. */
    @Test
    void anotherSchoolMayUseTheSameName() {
        add("Grade 6", "A");

        Tenant other = new Tenant();
        other.setId(UUID.randomUUID());
        other.setName("Elsewhere");
        other.setSubdomain("cs2-" + UUID.randomUUID());
        other.setActive(true);
        other.setCreatedAt(Instant.now());
        UUID otherTenant = tenantRepository.saveAndFlush(other).getId();

        AcademicYear otherYear = new AcademicYear();
        otherYear.setId(UUID.randomUUID());
        otherYear.setTenantId(otherTenant);
        otherYear.setName("2026-27");
        otherYear.setStartDate(LocalDate.of(2026, 4, 1));
        otherYear.setEndDate(LocalDate.of(2027, 3, 31));
        otherYear.setCurrent(true);
        UUID otherYearId = academicYearRepository.saveAndFlush(otherYear).getId();

        classStructureService.addSection(otherTenant, otherYearId, "Grade 6", "A", null, null, null);
        assertEquals(1, classStructureService.listSections(otherTenant).size());
    }

    /**
     * A timetable entry points at the section, so deleting the section leaves
     * periods belonging to a class that no longer exists.
     */
    @Test
    void aSectionWithTimetablePeriodsCannotBeDeleted() {
        add("Grade 6", "A");
        ClassSection section = classSectionRepository.findByTenantId(tenantId).get(0);

        TimetableEntry entry = new TimetableEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(tenantId);
        entry.setAcademicYearId(yearId);
        entry.setClassSection(section);
        entry.setTeacherId(UUID.randomUUID());
        entry.setDayOfWeek("MON");
        entry.setPeriodNumber(1);
        entry.setStartTime("08:00");
        entry.setEndTime("08:45");
        entry.setSubjectName("Mathematics");
        timetableRepository.saveAndFlush(entry);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> classStructureService.removeSection(section.getId(), tenantId, null));
        assertTrue(e.getMessage().contains("timetable"), e.getMessage());
        assertEquals(1, classStructureService.listSections(tenantId).size());
    }

    @Test
    void anEmptySectionCanStillBeDeleted() {
        add("Grade 6", "A");
        ClassSection section = classSectionRepository.findByTenantId(tenantId).get(0);

        classStructureService.removeSection(section.getId(), tenantId, null);
        assertTrue(classStructureService.listSections(tenantId).isEmpty());
    }

    /**
     * The rule the bulk importer always had and the manual form never did. A
     * guardian phone is how absence alerts and fee reminders go out, so a value
     * nobody can dial is a silent delivery failure.
     */
    @Test
    void phoneNumbersAreJudgedTheSameWayEverywhere() {
        assertFalse(PhoneNumbers.isValid("abc123"), "the reported value must be refused");
        assertFalse(PhoneNumbers.isValid(""));
        assertFalse(PhoneNumbers.isValid(null));
        assertFalse(PhoneNumbers.isValid("12345"), "too few digits to be a number");
        assertFalse(PhoneNumbers.isValid("((( )))-"), "punctuation alone is not a number");

        assertTrue(PhoneNumbers.isValid("+91 9876543210"));
        assertTrue(PhoneNumbers.isValid("9876543210"));
        assertTrue(PhoneNumbers.isValid("080-2345-6789"));
    }
}
