package com.schoolos.academics;

import com.schoolos.common.AuditLogService;
import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher/assessments")
public class TeacherAssessmentApiController {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private StudentAssessmentScoreRepository scoreRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogService auditLogService;

    private UUID resolveTeacherId(String username) {
        if (username == null) return null;
        return userRepository.findByEmail(username).map(u -> u.getId()).orElse(null);
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> createAssessment(@RequestBody CreateAssessmentRequest request, Authentication authentication) {
        ClassSection classSection = classSectionRepository.findById(request.getClassSectionId())
                .orElse(null);
        if (classSection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Class section not found"));
        }

        Assessment assessment = new Assessment();
        assessment.setId(UUID.randomUUID());
        assessment.setTenantId(classSection.getTenantId());
        assessment.setAcademicYearId(classSection.getAcademicYearId());
        assessment.setTitle(request.getTitle());
        assessment.setSubjectCode(request.getSubjectCode());
        assessment.setClassSection(classSection);
        assessment.setTerm(request.getTerm());
        assessment.setMaxScore(request.getMaxScore());
        assessment.setAssessmentDate(request.getAssessmentDate());
        assessment.setCreatedByTeacherId(resolveTeacherId(authentication != null ? authentication.getName() : null));

        assessmentRepository.save(assessment);
        auditLogService.log(authentication, "ASSESSMENT_CREATED", "Assessment", assessment.getId(),
                "Created assessment \"" + request.getTitle() + "\" (" + request.getSubjectCode() + ") for "
                        + classSection.getGradeName() + " - " + classSection.getSectionName());
        return ResponseEntity.ok(assessment);
    }

    @GetMapping("/class/{classSectionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> getAssessmentsForClass(@PathVariable UUID classSectionId) {
        ClassSection classSection = classSectionRepository.findById(classSectionId).orElse(null);
        if (classSection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Class section not found"));
        }
        return ResponseEntity.ok(assessmentRepository.findByClassSection(classSection));
    }

    @GetMapping("/{assessmentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> getAssessmentDetail(@PathVariable UUID assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assessment not found"));
        }

        List<Student> roster = studentRepository.findByClassSectionIn(List.of(assessment.getClassSection()));
        List<StudentAssessmentScore> existingScores = scoreRepository.findByAssessmentId(assessmentId);
        Map<UUID, Integer> scoreByStudentId = existingScores.stream()
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
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assessmentId}/scores")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> enterScores(@PathVariable UUID assessmentId,
                                          @RequestBody BulkScoreEntryRequest request,
                                          Authentication authentication) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if (assessment == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assessment not found"));
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
                    "gradedAt", result.getGradedAt().toString()
            );
        }).filter(s -> s != null).collect(Collectors.toList());

        auditLogService.log(authentication, "SCORES_ENTERED", "Assessment", assessmentId,
                "Entered " + saved.size() + " score(s) for assessment \"" + assessment.getTitle() + "\"");

        return ResponseEntity.ok(saved);
    }
}
