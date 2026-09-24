package com.concept.assessment;

import com.concept.assessment.app.AssessmentException;
import com.concept.assessment.app.AssessmentService;
import com.concept.assessment.app.BulkScoreEntryRequest;
import com.concept.assessment.app.CreateAssessmentRequest;
import com.concept.academics.data.StudentAssessmentScoreRepository;
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

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 150 out of 100 was saved, and reloading the page showed it still there.
 *
 * <p>This is the test the QA pass asked for and I did not write: the bad value
 * at the service, not through the screen. The screen's own guard is a red
 * border, which is a courtesy -- the rule has to hold for anything that can
 * reach the API.
 *
 * <p>Rejected rather than clamped, and the whole batch is checked before a
 * single row is written: a partial save tells the teacher it worked, grades
 * half the class, and says nothing about which half.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class ScoreBoundsTest {

    @Autowired private AssessmentService assessmentService;
    @Autowired private StudentAssessmentScoreRepository scoreRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ClassSectionRepository classSectionRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID yearId;
    private UUID assessmentId;
    private Student aarav;
    private Student kavya;
    private Authentication teacher;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Demo SSC");
        tenant.setSubdomain("score-" + UUID.randomUUID());
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

        ClassSection section = new ClassSection();
        section.setId(UUID.randomUUID());
        section.setTenantId(tenantId);
        section.setAcademicYearId(yearId);
        section.setGradeName("Grade 6");
        section.setSectionName("A");
        section = classSectionRepository.saveAndFlush(section);

        User priya = user("priya.score@example.com", "Priya Demo", UserRole.TEACHER);
        teacher = new UsernamePasswordAuthenticationToken(priya.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));

        aarav = student("Aarav", "Verma", section);
        kavya = student("Kavya", "Newjoin", section);

        CreateAssessmentRequest create = new CreateAssessmentRequest();
        set(create, "title", "Unit Test 1 - Fractions");
        set(create, "subjectCode", "MATH");
        set(create, "classSectionId", section.getId());
        set(create, "term", "TERM1");
        set(create, "maxScore", 100);
        set(create, "assessmentDate", LocalDate.now());
        // createAssessment is typed Object and returns the Assessment entity.
        assessmentId = ((com.concept.academics.data.Assessment)
                assessmentService.createAssessment(create, teacher)).getId();
    }

    /** The request objects are plain beans; setting by name keeps this readable. */
    private void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CreateAssessmentRequest has no field " + field, e);
        }
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

    private Student student(String first, String last, ClassSection section) {
        Student s = new Student();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setAcademicYearId(yearId);
        s.setFirstName(first);
        s.setLastName(last);
        s.setClassSection(section);
        return studentRepository.saveAndFlush(s);
    }

    private BulkScoreEntryRequest scores(Object... studentThenScore) {
        BulkScoreEntryRequest req = new BulkScoreEntryRequest();
        List<BulkScoreEntryRequest.ScoreEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < studentThenScore.length; i += 2) {
            BulkScoreEntryRequest.ScoreEntry e = new BulkScoreEntryRequest.ScoreEntry();
            set(e, "studentId", ((Student) studentThenScore[i]).getId());
            set(e, "score", studentThenScore[i + 1]);
            entries.add(e);
        }
        set(req, "scores", entries);
        return req;
    }

    /** The reported case: 150 on an assessment out of 100. */
    @Test
    void aScoreAboveTheMaximumIsRefused() {
        AssessmentException e = assertThrows(AssessmentException.class,
                () -> assessmentService.enterScores(assessmentId, scores(kavya, 150), teacher));

        assertTrue(e.getMessage().contains("Kavya"), "name the student: " + e.getMessage());
        assertTrue(e.getMessage().contains("100"), "name the maximum: " + e.getMessage());
    }

    @Test
    void aNegativeScoreIsRefused() {
        assertThrows(AssessmentException.class,
                () -> assessmentService.enterScores(assessmentId, scores(aarav, -5), teacher));
    }

    /**
     * The batch is refused whole. One bad mark among good ones used to leave the
     * good ones saved and report success, so the teacher had no way to know
     * which had landed.
     */
    @Test
    void oneBadScoreRejectsTheWholeBatchWithoutWritingAnything() {
        assertThrows(AssessmentException.class,
                () -> assessmentService.enterScores(assessmentId, scores(aarav, 85, kavya, 150), teacher));

        assertTrue(scoreRepository.findByStudentIdAndAssessmentId(aarav.getId(), assessmentId).isEmpty(),
                "Aarav's valid 85 must not have been written while Kavya's 150 was refused");
    }

    @Test
    void validScoresAreSaved() {
        assertDoesNotThrow(() ->
                assessmentService.enterScores(assessmentId, scores(aarav, 85, kavya, 92), teacher));

        assertEquals(85, scoreRepository.findByStudentIdAndAssessmentId(aarav.getId(), assessmentId)
                .orElseThrow().getScore());
    }

    /** The boundary itself is a legitimate mark. */
    @Test
    void fullMarksAreAllowed() {
        assertDoesNotThrow(() ->
                assessmentService.enterScores(assessmentId, scores(aarav, 100), teacher));
    }

    @Test
    void zeroIsAllowed() {
        assertDoesNotThrow(() ->
                assessmentService.enterScores(assessmentId, scores(aarav, 0), teacher));
    }
}
