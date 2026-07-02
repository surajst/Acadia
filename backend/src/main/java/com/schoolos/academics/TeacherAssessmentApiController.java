package com.schoolos.academics;

import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    private UUID resolveTeacherId(String username) {
        if (username == null) return UUID.fromString("11111111-1111-1111-1111-111111111111");
        return UUID.nameUUIDFromBytes(username.getBytes());
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

        return ResponseEntity.ok(saved);
    }
}
