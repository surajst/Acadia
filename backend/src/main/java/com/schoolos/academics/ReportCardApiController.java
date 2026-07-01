package com.schoolos.academics;

import com.schoolos.management.Parent;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.user.CurrentUserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ReportCardApiController {

    private final ReportCardService reportCardService;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;

    public ReportCardApiController(ReportCardService reportCardService,
                                    StudentRepository studentRepository,
                                    CurrentUserService currentUserService) {
        this.reportCardService = reportCardService;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
    }

    // ─── Teacher/Admin: generate for any student in their tenant ────────────

    @GetMapping("/api/teacher/students/{studentId}/report-card")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> teacherReportCard(@PathVariable UUID studentId,
                                                @RequestParam AssessmentTerm term) {
        if (studentRepository.findById(studentId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }
        return pdfResponse(reportCardService.generateReportCardPdf(studentId, term), term);
    }

    // ─── Parent: own child only ──────────────────────────────────────────────

    @GetMapping("/api/mobile/parent/report-card")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> parentReportCard(@RequestParam(required = false) UUID studentId,
                                               @RequestParam AssessmentTerm term,
                                               Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Parent not found"));
        }

        List<Student> children = studentRepository.findByParentsContaining(parent);
        Student target = (studentId != null)
                ? children.stream().filter(s -> s.getId().equals(studentId)).findFirst().orElse(null)
                : children.stream().findFirst().orElse(null);

        if (target == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this student"));
        }

        return pdfResponse(reportCardService.generateReportCardPdf(target.getId(), term), term);
    }

    // ─── Student: own record only ────────────────────────────────────────────

    @GetMapping("/api/student/report-card")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> studentReportCard(@RequestParam AssessmentTerm term, Authentication authentication) {
        Student student = currentUserService.getCurrentStudent(authentication).orElse(null);
        if (student == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }
        return pdfResponse(reportCardService.generateReportCardPdf(student.getId(), term), term);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, AssessmentTerm term) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .attachment()
                .filename("report_card_" + term.name() + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, org.springframework.http.HttpStatus.OK);
    }
}
