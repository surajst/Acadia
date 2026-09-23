package com.concept.attendance;

import com.concept.attendance.app.AttendanceService;
import com.concept.attendance.app.MarkAttendanceCommand;
import com.concept.attendance.data.AttendanceRecordRepository;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The register could only ever be taken for today, and the confirm dialog said
 * the action was final. Neither was true of how a school actually runs: a
 * teacher off sick on Monday needs Tuesday to enter Monday, and a wrong mark
 * needs correcting rather than living forever.
 *
 * <p>The correction case matters twice over. A second submission used to insert
 * a second row, so the day held two contradictory answers for one child -- and
 * it inflated the count of marked children that the dashboard attendance
 * percentage now divides by.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class AttendanceCorrectionTest {

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private Student aarav;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("att-" + UUID.randomUUID());
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
        UUID yearId = academicYearRepository.saveAndFlush(year).getId();

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        section = classSectionRepository.saveAndFlush(section);

        aarav = new Student();
        aarav.setId(UUID.randomUUID());
        aarav.setTenantId(tenantId);
        aarav.setAcademicYearId(yearId);
        aarav.setFirstName("Aarav");
        aarav.setLastName("Verma");
        aarav.setClassSection(section);
        aarav = studentRepository.saveAndFlush(aarav);
    }

    private void mark(String status, LocalDate on) {
        attendanceService.mark(
                new MarkAttendanceCommand(tenantId, List.of(aarav.getId()), List.of(status), on), null);
    }

    private List<Attendance> entriesOn(LocalDate date) {
        return attendanceRecordRepository.findAll().stream()
                .filter(a -> a.getStudent() != null && aarav.getId().equals(a.getStudent().getId()))
                .filter(a -> date.equals(a.getAttendanceDate()))
                .toList();
    }

    @Test
    void takingTheRegisterTwiceCorrectsItRatherThanDuplicatingIt() {
        mark("ABSENT", null);
        mark("PRESENT", null);

        List<Attendance> today = entriesOn(LocalDate.now());
        assertEquals(1, today.size(), "a correction must replace the mark, not add a second one");
        assertEquals(AttendanceStatus.PRESENT, today.get(0).getStatus(), "the later answer is the one meant");
    }

    @Test
    void aRegisterCanBeTakenForAnEarlierDay() {
        LocalDate monday = LocalDate.now().minusDays(3);
        mark("ABSENT", monday);

        assertEquals(1, entriesOn(monday).size());
        assertTrue(entriesOn(LocalDate.now()).isEmpty(),
                "backdating must not also mark today");
    }

    @Test
    void theRegisterCannotBeTakenForTheFuture() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> mark("PRESENT", LocalDate.now().plusDays(1)));
        assertTrue(e.getMessage().toLowerCase().contains("has not happened"), e.getMessage());
    }

    /** Something has to bound it, or last term is rewritable. */
    @Test
    void theRegisterCannotBeRewrittenBeyondTheWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> mark("PRESENT", LocalDate.now().minusDays(60)));
    }

    @Test
    void theEdgeOfTheWindowIsStillAllowed() {
        LocalDate edge = LocalDate.now().minusDays(30);
        mark("PRESENT", edge);
        assertEquals(1, entriesOn(edge).size());
    }
}
