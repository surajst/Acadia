package com.concept.tasks;

import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.TasksException;
import com.concept.tasks.app.TasksService;
import com.concept.tasks.data.TaskType;
import com.concept.tasks.data.TeacherTask;
import com.concept.tasks.data.TeacherTaskRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Handing in a task.
 *
 * <p>The submission path existed on the server and nothing had ever called it,
 * so it had never been exercised: the app listed tasks as flat, untappable
 * cards and a pupil could read their homework but not submit it. It also took
 * the XP to award as a request parameter, which the first caller could have set
 * to anything; the reward is read off the task now.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class StudentTaskSubmissionTest {

    @Autowired private TasksService tasksService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeacherTaskRepository teacherTaskRepository;
    @Autowired private AcademicSubmissionRepository submissionRepository;

    private UUID tenantId;
    private UUID yearId;
    private Student student;
    private User studentUser;
    private TeacherTask task;

    @BeforeEach
    public void setup() {
        tenantId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        String tag = tenantId.toString().substring(0, 8);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName("Submit Tenant");
        tenant.setSubdomain("submit-" + tag);
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

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        studentUser = new User();
        studentUser.setId(UUID.randomUUID());
        studentUser.setTenantId(tenantId);
        studentUser.setAcademicYearId(yearId);
        studentUser.setEmail("pupil-" + tag + "@school");
        studentUser.setPasswordHash("x");
        studentUser.setFullName("Aarav Pupil");
        studentUser.setRole(UserRole.STUDENT);
        studentUser.setActive(true);
        userRepository.saveAndFlush(studentUser);

        student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName("Aarav");
        student.setLastName("Pupil");
        student.setRollNumber("6A-01");
        student.setClassSection(section);
        student.setUserId(studentUser.getId());
        studentRepository.saveAndFlush(student);

        task = newTask(tenantId, yearId, 40);
    }

    private TeacherTask newTask(UUID tenant, UUID year, int xp) {
        TeacherTask t = new TeacherTask();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenant);
        t.setAcademicYearId(year);
        t.setTitle("Read chapter 4");
        t.setDescription("Then answer the questions.");
        t.setSubjectCode("ENGLISH");
        t.setTaskType(TaskType.READING);
        t.setStandard(6);
        t.setAssignedToClass(true);
        t.setXpReward(xp);
        t.setCreatedByTeacherId(UUID.randomUUID());
        return teacherTaskRepository.saveAndFlush(t);
    }

    private Authentication asStudent() {
        return new UsernamePasswordAuthenticationToken(studentUser.getEmail(), "x",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @Test
    public void handingInQueuesTheWorkForTheTeacher() {
        tasksService.submitTaskForCurrentStudent(task.getId(), "Finished it",
                List.of("Because it rained", "The fox"), asStudent());

        List<AcademicSubmission> queued = submissionRepository.findByStudentId(student.getId());
        assertEquals(1, queued.size());
        AcademicSubmission s = queued.get(0);
        assertEquals("PENDING", s.getStatus());
        assertEquals(task.getId(), s.getTeacherTaskId(), "the hand-in must point back at the task");
        assertEquals("Finished it", s.getProofOfWorkNotes());
        assertEquals("Because it rained", s.getAnswer1());
        assertEquals("The fox", s.getAnswer2());
    }

    @Test
    public void theXpComesFromTheTaskNotThePupil() {
        tasksService.submitTaskForCurrentStudent(task.getId(), null, List.of(), asStudent());

        assertEquals(40, submissionRepository.findByStudentId(student.getId()).get(0).getXpBounty(),
                "the reward is the teacher's, and must not be something the pupil can name");
    }

    /**
     * Renamed from handingInTwiceReplacesThePendingAttempt, because R3-P1-1
     * changed what should happen rather than how.
     *
     * <p>"A teacher should review one hand-in, not a pile of retries" is still the
     * point, and still asserted. What changed is which copy survives: replacing
     * the pending row let a pupil overwrite work their teacher was part-way
     * through reviewing, and the app left Hand in pressable because nothing in the
     * payload said a submission existed. The second attempt is now refused with
     * 409 and the first stands.
     *
     * <p>Work that was sent back is the exception, and StudentTaskStatusTest
     * covers it: REJECTED is an invitation to try again.
     */
    @Test
    public void handingInTwiceIsRefusedAndTheFirstAttemptStands() {
        tasksService.submitTaskForCurrentStudent(task.getId(), "first go", List.of(), asStudent());

        com.concept.tasks.app.TasksException e = org.junit.jupiter.api.Assertions.assertThrows(
                com.concept.tasks.app.TasksException.class,
                () -> tasksService.submitTaskForCurrentStudent(
                        task.getId(), "second go", List.of(), asStudent()));
        assertEquals(409, e.status(), "already handed in is a conflict, not a bad request");

        List<AcademicSubmission> queued = submissionRepository.findByStudentId(student.getId());
        assertEquals(1, queued.size(), "a teacher should review one hand-in, not a pile of retries");
        assertEquals("first go", queued.get(0).getProofOfWorkNotes(),
                "the attempt the teacher may already be reading must not be overwritten");
    }

    @Test
    public void aTaskFromAnotherSchoolCannotBeSubmittedAgainst() {
        UUID otherTenant = UUID.randomUUID();
        Tenant other = new Tenant();
        other.setId(otherTenant);
        other.setName("Other");
        other.setSubdomain("other-" + otherTenant.toString().substring(0, 8));
        other.setActive(true);
        other.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(other);
        TeacherTask theirs = newTask(otherTenant, yearId, 999);

        assertThrows(TasksException.class,
                () -> tasksService.submitTaskForCurrentStudent(theirs.getId(), null, List.of(), asStudent()));
    }

    @Test
    public void aMissingTaskIsRefused() {
        assertThrows(TasksException.class,
                () -> tasksService.submitTaskForCurrentStudent(null, null, List.of(), asStudent()));
        assertThrows(TasksException.class,
                () -> tasksService.submitTaskForCurrentStudent(UUID.randomUUID(), null, List.of(), asStudent()));
    }
}
