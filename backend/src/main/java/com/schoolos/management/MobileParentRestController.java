package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.academics.AssessmentService;
import com.schoolos.academics.SubjectPerformance;
import com.schoolos.user.UserRepository;
import com.schoolos.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
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
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssessmentService assessmentService;

    private Parent resolveParent(String username) {
        if (username == null) return null;
        
        String searchName = username;
        if (username.contains("@")) {
            User user = userRepository.findByEmail(username).orElse(null);
            if (user != null) {
                searchName = user.getFullName().split(" ")[0];
            }
        }
        
        final String finalSearch = searchName;
        Parent parent = parentRepository.findAll().stream()
                .filter(p -> finalSearch.equalsIgnoreCase(p.getFirstName()) || 
                            (p.getEmail() != null && p.getEmail().toLowerCase().startsWith(finalSearch.toLowerCase())))
                .findFirst()
                .orElse(null);
                
        if (parent == null) {
            parent = parentRepository.findAll().stream()
                    .filter(p -> "Rajesh".equals(p.getFirstName()) || "Ramesh".equals(p.getFirstName()))
                    .findFirst()
                    .orElseGet(() -> parentRepository.findAll().stream().findFirst().orElse(null));
        }
        
        return parent;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "ramesh";
        Parent parent = resolveParent(username);

        Student student = null;
        if (parent != null) {
            List<Student> students = studentRepository.findByParentsContaining(parent);
            if (!students.isEmpty()) {
                student = students.get(0);
            }
        }

        if (student == null) {
            student = studentRepository.findAll().stream().findFirst().orElse(null);
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

        // Attendance Status
        String attendanceStatus = "NOT MARKED";
        List<Attendance> attendances = attendanceRepository.findAll().stream()
            .filter(a -> a.getStudent() != null && sId.equals(a.getStudent().getId()))
            .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        Attendance todayAttendance = attendances.stream()
            .filter(a -> today.equals(a.getAttendanceDate()))
            .findFirst()
            .orElse(null);

        if (todayAttendance != null) {
            attendanceStatus = todayAttendance.getStatus().name();
        } else if (!attendances.isEmpty()) {
            attendances.sort((a, b) -> b.getAttendanceDate().compareTo(a.getAttendanceDate()));
            attendanceStatus = attendances.get(0).getStatus().name();
        }

        Map<String, Object> response = new HashMap<>();

        // Parent Info
        if (parent != null) {
            Map<String, Object> parentInfo = new HashMap<>();
            parentInfo.put("id", parent.getId());
            parentInfo.put("firstName", parent.getFirstName());
            parentInfo.put("lastName", parent.getLastName());
            response.put("parent", parentInfo);
        }

        // Student Info
        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("id", student.getId());
        studentInfo.put("firstName", student.getFirstName());
        studentInfo.put("lastName", student.getLastName());
        if (student.getClassSection() != null) {
            studentInfo.put("gradeName", student.getClassSection().getGradeName());
            studentInfo.put("sectionName", student.getClassSection().getSectionName());
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
        response.put("subjectPerformance", assessmentService.getSubjectPerformance(studentId));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/subject-performance")
    public ResponseEntity<?> getSubjectPerformance(
            @RequestParam(value = "studentId", required = false) UUID studentId,
            Authentication authentication) {

        if (studentId == null) {
            String username = (authentication != null) ? authentication.getName() : "ramesh";
            Parent parent = resolveParent(username);
            if (parent != null) {
                List<Student> students = studentRepository.findByParentsContaining(parent);
                if (!students.isEmpty()) {
                    studentId = students.get(0).getId();
                }
            }
        }

        if (studentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No student found"));
        }

        return ResponseEntity.ok(assessmentService.getSubjectPerformance(studentId));
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> getAttendanceLog(
            @RequestParam(value = "studentId", required = false) UUID studentId,
            Authentication authentication) {

        // If no studentId provided, resolve from the authenticated parent
        if (studentId == null) {
            String username = (authentication != null) ? authentication.getName() : "ramesh";
            Parent parent = resolveParent(username);
            if (parent != null) {
                List<Student> students = studentRepository.findByParentsContaining(parent);
                if (!students.isEmpty()) {
                    studentId = students.get(0).getId();
                }
            }
        }

        if (studentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No student found"));
        }

        final UUID sid = studentId;
        List<Map<String, String>> log = attendanceRepository.findAll().stream()
                .filter(a -> a.getStudent() != null && sid.equals(a.getStudent().getId()))
                .sorted(Comparator.comparing(Attendance::getAttendanceDate))
                .map(a -> {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("date", a.getAttendanceDate().toString());
                    entry.put("status", a.getStatus().name());
                    return entry;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(log);
    }
}
