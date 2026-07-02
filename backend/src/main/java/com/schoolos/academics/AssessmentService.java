package com.schoolos.academics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    @Autowired
    private StudentAssessmentScoreRepository scoreRepository;

    /**
     * Aggregates a student's scores per subject: average percentage across all
     * graded assessments, plus the trend (chronological list of percentages)
     * so the client can render a sparkline.
     */
    public List<SubjectPerformance> getSubjectPerformance(UUID studentId) {
        List<StudentAssessmentScore> scores = scoreRepository.findByStudentId(studentId);

        Map<String, List<StudentAssessmentScore>> bySubject = scores.stream()
                .collect(Collectors.groupingBy(s -> s.getAssessment().getSubjectCode()));

        return bySubject.entrySet().stream()
                .map(entry -> {
                    List<StudentAssessmentScore> subjectScores = entry.getValue().stream()
                            .sorted(Comparator.comparing(s -> s.getAssessment().getAssessmentDate()))
                            .collect(Collectors.toList());

                    List<Double> percentages = subjectScores.stream()
                            .map(s -> 100.0 * s.getScore() / s.getAssessment().getMaxScore())
                            .collect(Collectors.toList());

                    double average = percentages.stream()
                            .mapToDouble(Double::doubleValue)
                            .average()
                            .orElse(0.0);

                    return new SubjectPerformance(entry.getKey(), Math.round(average * 10) / 10.0, percentages);
                })
                .sorted(Comparator.comparing(SubjectPerformance::subjectCode))
                .collect(Collectors.toList());
    }
}
