package com.concept.oversight;

import com.concept.oversight.app.OversightException;
import com.concept.oversight.app.OversightService;
import com.concept.oversight.data.StudentProgress;
import com.concept.oversight.data.StudentProgressRepository;
import com.concept.curriculum.data.Curriculum;
import com.concept.curriculum.data.CurriculumRepository;
import com.concept.curriculum.data.SyllabusType;
import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.tasks.app.TasksService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * School A must not be able to see or act on school B's approval queue.
 *
 * <p>Each of these covered a real hole. The sharpest was
 * {@code /api/academic/teacher/pending}, which called an unscoped
 * findByStatus and so handed every school's pending submissions to any
 * teacher who asked -- no id to guess, no ownership check. The rest were
 * request-supplied ids resolved with a bare findById and then mutated:
 * approve or reject on another school's progress row or submission.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class CrossTenantApprovalTest {

    @Autowired private OversightService oversightService;
    @Autowired private TasksService tasksService;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private CurriculumRepository curriculumRepository;
    @Autowired private StudentProgressRepository studentProgressRepository;
    @Autowired private AcademicSubmissionRepository academicSubmissionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantA;
    private UUID tenantB;
    private StudentProgress progressB;
    private AcademicSubmission submissionB;

    private UUID makeTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Tenant " + tenant.getId());
        tenant.setSubdomain("t-" + tenant.getId());
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        return tenantRepository.saveAndFlush(tenant).getId();
    }

    private UUID makeYear(UUID tenantId) {
        AcademicYear year = new AcademicYear();
        year.setId(UUID.randomUUID());
        year.setTenantId(tenantId);
        year.setName("2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year.setCurrent(true);
        return academicYearRepository.saveAndFlush(year).getId();
    }

    private Student makeStudent(UUID tenantId, UUID yearId) {
        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 1");
        section.setSectionName("A");
        classSectionRepository.saveAndFlush(section);

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(yearId);
        student.setFirstName("Pupil");
        student.setLastName("B");
        student.setClassSection(section);
        return studentRepository.saveAndFlush(student);
    }

    @BeforeEach
    public void setup() {
        tenantA = makeTenant();
        tenantB = makeTenant();
        UUID yearB = makeYear(tenantB);
        makeYear(tenantA);
        Student studentB = makeStudent(tenantB, yearB);

        Curriculum topicB = new Curriculum();
        topicB.setId(UUID.randomUUID());
        topicB.setTenantId(tenantB);
        topicB.setAcademicYearId(yearB);
        topicB.setSyllabusType(SyllabusType.CBSE);
        topicB.setStandard(1);
        topicB.setSubjectCode("MATH");
        topicB.setTopicName("B Topic");
        topicB.setTopicOrder(1);
        topicB.setXpReward(50);
        curriculumRepository.saveAndFlush(topicB);

        progressB = new StudentProgress();
        progressB.setStudent(studentB);
        progressB.setCurriculum(topicB);
        progressB.setStatus("PENDING");
        studentProgressRepository.saveAndFlush(progressB);

        submissionB = new AcademicSubmission(studentB.getId(), "B Skill", 100);
        academicSubmissionRepository.saveAndFlush(submissionB);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pendingSubmissions_doesNotLeakAnotherSchoolsQueue() {
        // The bug: an unscoped findByStatus("PENDING") returned every school's
        // submissions to whichever teacher called the endpoint.
        List<AcademicSubmission> forA = (List<AcademicSubmission>) tasksService.pendingSubmissions(tenantA);
        assertTrue(forA.stream().noneMatch(s -> s.getId().equals(submissionB.getId())),
                "School A's pending queue must not contain school B's submission");

        // ...and school B still sees its own, so this is a boundary, not a mute.
        List<AcademicSubmission> forB = (List<AcademicSubmission>) tasksService.pendingSubmissions(tenantB);
        assertTrue(forB.stream().anyMatch(s -> s.getId().equals(submissionB.getId())),
                "School B must still see its own submission");
    }

    @Test
    public void approveXp_refusesAnotherSchoolsSubmission() {
        assertThrows(RuntimeException.class,
                () -> tasksService.approveXp(submissionB.getId(), tenantA));
        assertEquals("PENDING",
                academicSubmissionRepository.findById(submissionB.getId()).orElseThrow().getStatus(),
                "the submission must be untouched after a cross-tenant attempt");
    }

    @Test
    public void approveProgress_refusesAnotherSchoolsRow() {
        assertThrows(OversightException.class,
                () -> oversightService.approveProgress(progressB.getId(), tenantA));
        assertEquals("PENDING",
                studentProgressRepository.findById(progressB.getId()).orElseThrow().getStatus());
    }

    @Test
    public void rejectProgress_refusesAnotherSchoolsRow() {
        assertThrows(OversightException.class,
                () -> oversightService.rejectProgress(progressB.getId(), "nope", tenantA));
        assertEquals("PENDING",
                studentProgressRepository.findById(progressB.getId()).orElseThrow().getStatus());
    }

    @Test
    public void rejectMilestone_refusesAnotherSchoolsSubmission() {
        assertThrows(OversightException.class,
                () -> oversightService.rejectMilestone(submissionB.getId(), "nope", tenantA));
        assertEquals("PENDING",
                academicSubmissionRepository.findById(submissionB.getId()).orElseThrow().getStatus());
    }

    @Test
    public void ownTenantApprovalStillWorks() {
        // The guard must not have broken the legitimate path.
        oversightService.approveProgress(progressB.getId(), tenantB);
        assertEquals("APPROVED",
                studentProgressRepository.findById(progressB.getId()).orElseThrow().getStatus());
    }
}
