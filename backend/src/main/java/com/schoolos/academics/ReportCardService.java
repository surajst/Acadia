package com.schoolos.academics;

import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.tenant.Tenant;
import com.schoolos.tenant.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportCardService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentAssessmentScoreRepository scoreRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private SubjectService subjectService;

    public byte[] generateReportCardPdf(UUID studentId, AssessmentTerm term) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        List<StudentAssessmentScore> termScores = scoreRepository.findByStudentId(studentId).stream()
                .filter(s -> s.getAssessment().getTerm() == term)
                .collect(Collectors.toList());

        Map<String, String> subjectDisplayNames = subjectService.listAllSubjects(student.getTenantId()).stream()
                .collect(Collectors.toMap(Subject::getCode, Subject::getDisplayName, (a, b) -> a));

        Map<String, List<StudentAssessmentScore>> bySubject = termScores.stream()
                .collect(Collectors.groupingBy(s -> s.getAssessment().getSubjectCode()));

        List<Map<String, Object>> subjectRows = bySubject.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> buildSubjectRow(
                        subjectDisplayNames.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue()))
                .collect(Collectors.toList());

        double overallAverage = subjectRows.stream()
                .mapToDouble(row -> (double) row.get("averagePercentage"))
                .average()
                .orElse(0.0);

        Tenant tenant = tenantRepository.findById(student.getTenantId()).orElse(null);

        Context context = new Context();
        context.setVariable("schoolName", tenant != null ? tenant.getName() : "");
        context.setVariable("studentName", student.getFirstName() + " " + student.getLastName());
        context.setVariable("rollNumber", student.getRollNumber());
        context.setVariable("gradeName", student.getClassSection() != null ? student.getClassSection().getGradeName() : "");
        context.setVariable("sectionName", student.getClassSection() != null ? student.getClassSection().getSectionName() : "");
        context.setVariable("term", term.name());
        context.setVariable("generatedDate", LocalDate.now().format(DATE_FMT));
        context.setVariable("subjects", subjectRows);
        context.setVariable("hasSubjects", !subjectRows.isEmpty());
        context.setVariable("overallAverage", round1(overallAverage));
        context.setVariable("overallGrade", letterGrade(overallAverage));

        String html = templateEngine.process("report_card", context);
        return renderPdf(html);
    }

    private Map<String, Object> buildSubjectRow(String subjectDisplayName, List<StudentAssessmentScore> scores) {
        List<Map<String, Object>> assessmentRows = scores.stream()
                .sorted(Comparator.comparing(s -> s.getAssessment().getAssessmentDate()))
                .map(s -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("title", s.getAssessment().getTitle());
                    row.put("date", s.getAssessment().getAssessmentDate().format(DATE_FMT));
                    row.put("score", s.getScore());
                    row.put("maxScore", s.getAssessment().getMaxScore());
                    return row;
                })
                .collect(Collectors.toList());

        double average = scores.stream()
                .mapToDouble(s -> 100.0 * s.getScore() / s.getAssessment().getMaxScore())
                .average()
                .orElse(0.0);

        Map<String, Object> row = new HashMap<>();
        row.put("subjectName", subjectDisplayName);
        row.put("assessments", assessmentRows);
        row.put("averagePercentage", round1(average));
        row.put("grade", letterGrade(average));
        return row;
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private String letterGrade(double percentage) {
        if (percentage >= 90) return "A";
        if (percentage >= 75) return "B";
        if (percentage >= 60) return "C";
        if (percentage >= 40) return "D";
        return "F";
    }

    private byte[] renderPdf(String html) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            renderer.createPDF(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render report card PDF", e);
        }
    }
}
