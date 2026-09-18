package com.concept.tasks;

import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.AttendancePayload;
import com.concept.tasks.app.TasksException;
import com.concept.tasks.app.TasksService;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two fixes, both in {@link TasksService}.
 *
 * <p>{@code searchMyStudents} returned an empty list for every real teacher: it
 * invented a teacher id as {@code UUID.nameUUIDFromBytes(email)} and looked up
 * {@code ClassSection.teacherId}, a column only the dev-mode seeders write. The
 * task-assignment autocomplete was simply dead in production.
 *
 * <p>{@code submitAttendance} could only ever write {@code LocalDate.now()}, so
 * a teacher who missed a day had no way to go back and mark it.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class TasksServiceTeacherScopeTest {

    @Autowired private TasksService tasksService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubjectAssignmentRepository subjectAssignmentRepository;

    private UUID tenantId;
    private UUID yearId;
    private ClassSection taught;
    private ClassSection notTaught;
    private User teacher;
    private Student mine;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        String tag = tenantId.toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Tasks Tenant");
        tenant.setSubdomain("tasks-" + tag);
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

        taught = section("Grade 6", "A");
        notTaught = section("Grade 7", "B");

        teacher = new User();
        teacher.setId(UUID.randomUUID());
        teacher.setTenantId(tenantId);
        teacher.setAcademicYearId(yearId);
        teacher.setEmail("teacher-" + tag + "@school.edu");
        teacher.setPasswordHash("x");
        teacher.setFullName("Test Teacher");
        teacher.setRole(UserRole.TEACHER);
        teacher.setActive(true);
        userRepository.saveAndFlush(teacher);

        SubjectAssignment assignment = new SubjectAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(tenantId);
        assignment.setAcademicYearId(yearId);
        assignment.setTeacher(teacher);
        assignment.setClassSection(taught);
        assignment.setSubjectName("Mathematics");
        assignment.setHomeClass(true);
        subjectAssignmentRepository.saveAndFlush(assignment);

        mine = student("Aarav", taught);
        student("Distant", notTaught);
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

    private Student student(String first, ClassSection section) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName("Pupil");
        s.setRollNumber(first + "-1");
        s.setClassSection(section);
        return studentRepository.saveAndFlush(s);
    }

    private Authentication asTeacher() {
        return new UsernamePasswordAuthenticationToken(teacher.getEmail(), "x",
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    // ── P4: the autocomplete that always came back empty ────────────────────

    @Test
    public void searchFindsTheTeachersOwnStudents() {
        List<Map<String, String>> found = tasksService.searchMyStudents("", asTeacher());

        List<String> names = found.stream().map(m -> m.get("name")).collect(Collectors.toList());
        assertTrue(names.contains("Aarav Pupil"),
                "a teacher must see their own class in the task autocomplete; got " + names);
    }

    @Test
    public void searchDoesNotReachStudentsTheTeacherDoesNotTeach() {
        List<String> names = tasksService.searchMyStudents("", asTeacher())
                .stream().map(m -> m.get("name")).collect(Collectors.toList());

        assertTrue(!names.contains("Distant Pupil"),
                "the autocomplete must stay inside the teacher's own sections; got " + names);
    }

    @Test
    public void searchStillFiltersByTheQuery() {
        assertTrue(tasksService.searchMyStudents("zzz", asTeacher()).isEmpty(),
                "a query matching nobody should return nobody");
        assertEquals(1, tasksService.searchMyStudents("aara", asTeacher()).size(),
                "a query matching one pupil should return exactly them");
    }

    // ── P5: marking a register for a day other than today ───────────────────

    private void submit(LocalDate date, AttendanceStatus status) {
        tasksService.submitAttendance(
                new AttendancePayload(date,
                        List.of(new AttendancePayload.AttendanceEntry(mine.getId(), status, ""))),
                asTeacher());
    }

    @Test
    public void attendanceDefaultsToTodayWhenNoDateIsSent() {
        submit(null, AttendanceStatus.PRESENT);

        List<Attendance> rows = attendanceRepository
                .findByClassSectionAndAttendanceDate(taught, LocalDate.now());
        assertEquals(1, rows.size(), "an absent date must still mean today, as every existing client sends");
    }

    @Test
    public void teacherCanMarkADayTheyMissed() {
        LocalDate monday = LocalDate.now().minusDays(3);

        submit(monday, AttendanceStatus.ABSENT);

        List<Attendance> rows = attendanceRepository.findByClassSectionAndAttendanceDate(taught, monday);
        assertEquals(1, rows.size(), "a missed day must be markable after the fact");
        assertEquals(AttendanceStatus.ABSENT, rows.get(0).getStatus());
    }

    @Test
    public void correctingADayReplacesItRatherThanDuplicating() {
        LocalDate monday = LocalDate.now().minusDays(3);

        submit(monday, AttendanceStatus.ABSENT);
        submit(monday, AttendanceStatus.PRESENT);

        List<Attendance> rows = attendanceRepository.findByClassSectionAndAttendanceDate(taught, monday);
        assertEquals(1, rows.size(), "re-marking a day must overwrite, not stack up rows");
        assertEquals(AttendanceStatus.PRESENT, rows.get(0).getStatus());
    }

    @Test
    public void theFutureCannotBeMarked() {
        TasksException e = assertThrows(TasksException.class,
                () -> submit(LocalDate.now().plusDays(1), AttendanceStatus.PRESENT));
        assertTrue(e.getMessage().toLowerCase().contains("future"), e.getMessage());
    }

    @Test
    public void backfillIsBoundedSoOldTermsCannotBeRewritten() {
        TasksException e = assertThrows(TasksException.class,
                () -> submit(LocalDate.now().minusDays(400), AttendanceStatus.PRESENT));
        assertTrue(e.getMessage().toLowerCase().contains("corrected"), e.getMessage());
    }
}
