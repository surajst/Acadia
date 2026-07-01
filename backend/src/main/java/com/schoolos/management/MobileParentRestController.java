package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.parentapp.AttendanceRecord;
import com.schoolos.parentapp.DateRange;
import com.schoolos.parentapp.SisDataProvider;
import com.schoolos.parentapp.StudentSummary;
import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mobile/parent")
public class MobileParentRestController {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private AcademicSubmissionRepository academicSubmissionRepository;

    @Autowired
    private ParentRewardRepository parentRewardRepository;

    @Autowired
    private ParentQuestRepository parentQuestRepository;

    @Autowired
    private SisDataProvider sisDataProvider;

    @Autowired
    private CurrentUserService currentUserService;

    private UUID resolveStudentId(UUID studentId, Authentication authentication) {
        if (studentId != null) return studentId;
        return currentUserService.getCurrentParent(authentication)
                .map(parent -> studentRepository.findByParentsContaining(parent))
                .filter(students -> !students.isEmpty())
                .map(students -> students.get(0).getId())
                .orElse(null);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData(Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);

        Student student = null;
        if (parent != null) {
            List<Student> students = studentRepository.findByParentsContaining(parent);
            if (!students.isEmpty()) {
                student = students.get(0);
            }
        }

        if (student == null) {
            return ResponseEntity.badRequest().body("No student found for this parent.");
        }

        UUID studentId = student.getId();

        StudentMetric studentMetrics = studentMetricRepository.findByStudentId(studentId).orElse(new StudentMetric());

        int totalXp = studentMetrics.getSchoolXp() != null ? studentMetrics.getSchoolXp() : 0;
        int scholarLevel = (totalXp / 500) + 1;
        int levelProgress = (totalXp % 500) * 100 / 500;
        int xpToNextLevel = 500 - (totalXp % 500);

        List<AcademicSubmission> submissions = academicSubmissionRepository.findByStudentId(studentId);
        List<ParentReward> pendingRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "PENDING");
        List<ParentQuest> parentQuests = parentQuestRepository.findByStudentId(studentId);

        final UUID sId = studentId;
        List<ParentReward> parentRewards = parentRewardRepository.findAll().stream()
            .filter(r -> r.getStudent() != null && sId.equals(r.getStudent().getId()))
            .collect(Collectors.toList());

        // Attendance Status — via SisDataProvider (most-recent-first, full history), fall back to "not marked"
        List<AttendanceRecord> allAttendance = sisDataProvider.getAttendance(studentId,
                new DateRange(java.time.LocalDate.of(2000, 1, 1), java.time.LocalDate.now()));
        String attendanceStatus = allAttendance.isEmpty() ? "NOT MARKED" : allAttendance.get(0).status();

        Map<String, Object> response = new HashMap<>();

        // Parent Info
        Map<String, Object> parentInfo = new HashMap<>();
        parentInfo.put("id", parent.getId());
        parentInfo.put("firstName", parent.getFirstName());
        parentInfo.put("lastName", parent.getLastName());
        response.put("parent", parentInfo);

        // Student Info — via SisDataProvider
        StudentSummary studentSummary = sisDataProvider.getStudent(studentId).orElse(null);
        Map<String, Object> studentInfo = new HashMap<>();
        if (studentSummary != null) {
            studentInfo.put("id", studentSummary.id());
            studentInfo.put("firstName", studentSummary.firstName());
            studentInfo.put("lastName", studentSummary.lastName());
            if (studentSummary.gradeName() != null) {
                studentInfo.put("gradeName", studentSummary.gradeName());
                studentInfo.put("sectionName", studentSummary.sectionName());
            }
        }
        response.put("student", studentInfo);

        // Metrics
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("schoolXp", studentMetrics.getSchoolXp());
        metrics.put("parentXp", studentMetrics.getParentXp());
        metrics.put("activeStreak", studentMetrics.getActiveStreak());
        metrics.put("scholarLevel", scholarLevel);
        metrics.put("levelProgress", levelProgress);
        metrics.put("xpToNextLevel", xpToNextLevel);
        metrics.put("totalXp", totalXp);
        response.put("metrics", metrics);

        response.put("attendanceStatus", attendanceStatus);
        response.put("submissions", submissions);
        response.put("pendingRewards", pendingRewards);
        response.put("parentQuests", parentQuests);
        response.put("parentRewards", parentRewards);
        response.put("subjectPerformance", sisDataProvider.getSubjectPerformance(studentId));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/subject-performance")
    public ResponseEntity<?> getSubjectPerformance(
            @RequestParam(value = "studentId", required = false) UUID studentId,
            Authentication authentication) {

        UUID resolvedId = resolveStudentId(studentId, authentication);
        if (resolvedId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No student found"));
        }

        return ResponseEntity.ok(sisDataProvider.getSubjectPerformance(resolvedId));
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> getAttendanceLog(
            @RequestParam(value = "studentId", required = false) UUID studentId,
            Authentication authentication) {

        UUID resolvedId = resolveStudentId(studentId, authentication);
        if (resolvedId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No student found"));
        }

        // Full history, oldest-first, to match prior behavior
        List<Map<String, String>> log = sisDataProvider.getAttendance(resolvedId, new DateRange(java.time.LocalDate.of(2000, 1, 1), java.time.LocalDate.now()))
                .stream()
                .sorted((a, b) -> a.date().compareTo(b.date()))
                .map(a -> {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("date", a.date().toString());
                    entry.put("status", a.status());
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(log);
    }
}
