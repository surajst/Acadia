package com.concept.assessment.app;

import com.concept.academics.Assessment;
import com.concept.academics.AssessmentRepository;
import com.concept.academics.AssessmentTerm;
import com.concept.academics.ReportCardService;
import com.concept.academics.StudentAssessmentScore;
import com.concept.academics.StudentAssessmentScoreRepository;
import com.concept.common.AuditLogService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for teacher assessments (create, score entry, detail) and
 * report-card PDF generation for teacher/parent/student. Owns tenant resolution,
 * ownership checks, term parsing, and the audit trail so the web layer only
 * binds requests and shapes HTTP responses (ADR 0001).
 *
 * <p>Entity-shaped JSON (created assessment, class assessment list) is returned
 * as {@code Object}: the serialized shape is preserved with no static entity
 * dependency in the web layer.
 */
// Explicit bean name avoids a collision with the existing academics.AssessmentService.
// Injection is by type, so controllers are unaffected.
@Service("assessmentSliceService")
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final StudentAssessmentScoreRepository scoreRepository;
    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ReportCardService reportCardService;
    private final CurrentUserService currentUserService;

    public AssessmentService(AssessmentRepository assessmentRepository,
                             StudentAssessmentScoreRepository scoreRepository,
                             ClassSectionRepository classSectionRepository,
                             StudentRepository studentRepository,
                             UserRepository userRepository,
                             AuditLogService auditLogService,
                             ReportCardService reportCardService,
                             CurrentUserService currentUserService) {
        this.assessmentRepository = assessmentRepository;
        this.scoreRepository = scoreRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.reportCardService = reportCardService;
        this.currentUserService = currentUserService;
    }

    // ─── Teacher assessments ────────────────────────────────────────────────

    public Object createAssessment(CreateAssessmentRequest request, Authentication authentication) {
        ClassSection classSection = classSectionRepository.findById(request.getClassSectionId()).orElse(null);
        if (classSection == null) {
            throw AssessmentException.badRequest("Class section not found");
        }
        Assessment assessment = new Assessment();
        assessment.setId(UUID.randomUUID());
        assessment.setTenantId(classSection.getTenantId());
        assessment.setAcademicYearId(classSection.getAcademicYearId());
        assessment.setTitle(request.getTitle());
        assessment.setSubjectCode(request.getSubjectCode());
        assessment.setClassSection(classSection);
        assessment.setTerm(parseTerm(request.getTerm()));
        assessment.setMaxScore(request.getMaxScore());
        assessment.setAssessmentDate(request.getAssessmentDate());
        assessment.setCreatedByTeacherId(resolveTeacherId(authentication != null ? authentication.getName() : null));
        assessmentRepository.save(assessment);

        auditLogService.log(authentication, "ASSESSMENT_CREATED", "Assessment", assessment.getId(),
                "Created assessment \"" + request.getTitle() + "\" (" + request.getSubjectCode() + ") for "
                        + classSection.getGradeName() + " - " + classSection.getSectionName());
        return assessment;
    }

    public Object assessmentsForClass(UUID classSectionId) {
        ClassSection classSection = classSectionRepository.findById(classSectionId).orElse(null);
        if (classSection == null) {
            throw AssessmentException.badRequest("Class section not found");
        }
        return assessmentRepository.findByClassSection(classSection);
    }

    public Map<String, Object> assessmentDetail(UUID assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment == null) {
            throw AssessmentException.badRequest("Assessment not found");
        }
        List<Student> roster = studentRepository.findByClassSectionIn(List.of(assessment.getClassSection()));
        Map<UUID, Integer> scoreByStudentId = scoreRepository.findByAssessmentId(assessmentId).stream()
                .collect(Collectors.toMap(s -> s.getStudent().getId(), StudentAssessmentScore::getScore));

        List<Map<String, Object>> rosterWithScores = roster.stream().map(student -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", student.getId());
            row.put("studentName", student.getFirstName() + " " + student.getLastName());
            row.put("rollNumber", student.getRollNumber());
            row.put("score", scoreByStudentId.get(student.getId()));
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", assessment.getId());
        response.put("title", assessment.getTitle());
        response.put("subjectCode", assessment.getSubjectCode());
        response.put("term", assessment.getTerm());
        response.put("maxScore", assessment.getMaxScore());
        response.put("assessmentDate", assessment.getAssessmentDate());
        response.put("roster", rosterWithScores);
        return response;
    }

    public List<Map<String, Object>> enterScores(UUID assessmentId, BulkScoreEntryRequest request,
                                                 Authentication authentication) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment == null) {
            throw AssessmentException.badRequest("Assessment not found");
        }
        UUID teacherId = resolveTeacherId(authentication != null ? authentication.getName() : null);

        List<Map<String, Object>> saved = request.getScores().stream().map(entry -> {
            Student student = studentRepository.findById(entry.getStudentId()).orElse(null);
            if (student == null) return null;
            StudentAssessmentScore score = scoreRepository
                    .findByStudentIdAndAssessmentId(entry.getStudentId(), assessmentId)
                    .orElse(new StudentAssessmentScore());
            score.setStudent(student);
            score.setAssessment(assessment);
            score.setScore(entry.getScore());
            score.setGradedByTeacherId(teacherId);
            StudentAssessmentScore result = scoreRepository.save(score);
            return Map.<String, Object>of(
                    "id", result.getId(),
                    "studentId", entry.getStudentId(),
                    "score", result.getScore(),
                    "gradedAt", result.getGradedAt().toString());
        }).filter(s -> s != null).collect(Collectors.toList());

        auditLogService.log(authentication, "SCORES_ENTERED", "Assessment", assessmentId,
                "Entered " + saved.size() + " score(s) for assessment \"" + assessment.getTitle() + "\"");
        return saved;
    }

    // ─── Report cards ───────────────────────────────────────────────────────

    public byte[] teacherReportCard(UUID studentId, String term) {
        if (studentRepository.findById(studentId).isEmpty()) {
            throw AssessmentException.badRequest("Student not found");
        }
        return reportCardService.generateReportCardPdf(studentId, parseTerm(term));
    }

    public byte[] parentReportCard(UUID studentId, String term, Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            throw AssessmentException.badRequest("Parent not found");
        }
        List<Student> children = studentRepository.findByParentsContaining(parent);
        Student target = (studentId != null)
                ? children.stream().filter(s -> s.getId().equals(studentId)).findFirst().orElse(null)
                : children.stream().findFirst().orElse(null);
        if (target == null) {
            throw AssessmentException.forbidden("Not authorized for this student");
        }
        return reportCardService.generateReportCardPdf(target.getId(), parseTerm(term));
    }

    public byte[] studentReportCard(String term, Authentication authentication) {
        Student student = currentUserService.getCurrentStudent(authentication).orElse(null);
        if (student == null) {
            throw AssessmentException.badRequest("Student not found");
        }
        return reportCardService.generateReportCardPdf(student.getId(), parseTerm(term));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private AssessmentTerm parseTerm(String term) {
        try {
            return AssessmentTerm.valueOf(term);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw AssessmentException.badRequest("Invalid term: " + term);
        }
    }

    private UUID resolveTeacherId(String username) {
        if (username == null) return null;
        return userRepository.findByEmail(username).map(u -> u.getId()).orElse(null);
    }
}
