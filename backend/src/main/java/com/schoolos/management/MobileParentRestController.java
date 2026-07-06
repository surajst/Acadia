package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.parentapp.AttendanceRecord;
import com.schoolos.parentapp.DateRange;
import com.schoolos.parentapp.SisDataProvider;
import com.schoolos.parentapp.StudentSummary;
import com.schoolos.transport.BusRoute;
import com.schoolos.transport.BusRouteRepository;
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

    @Autowired
    private BusRouteRepository busRouteRepository;

    /**
     * Resolves the student a parent-scoped request should act on. If a
     * studentId is supplied it must belong to one of this parent's own
     * linked children — otherwise falls back to the first linked child.
     * This prevents a parent from reading another family's data by simply
     * passing a different studentId query param.
     */
    private UUID resolveStudentId(UUID studentId, Authentication authentication) {
        List<Student> children = currentUserService.getCurrentParent(authentication)
                .map(parent -> studentRepository.findByParentsContaining(parent))
                .orElse(List.of());
        if (children.isEmpty()) return null;
        if (studentId != null) {
            boolean owned = children.stream().anyMatch(s -> s.getId().equals(studentId));
            if (owned) return studentId;
        }
        return children.get(0).getId();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData(
            @RequestParam(value = "studentId", required = false) UUID requestedStudentId,
            Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            return ResponseEntity.badRequest().body("No student found for this parent.");
        }

        List<Student> children = studentRepository.findByParentsContaining(parent);
        if (children.isEmpty()) {
            return ResponseEntity.badRequest().body("No student found for this parent.");
        }

        Student student = children.stream()
                .filter(s -> requestedStudentId != null && s.getId().equals(requestedStudentId))
                .findFirst()
                .orElse(children.get(0));

        UUID studentId = student.getId();

        StudentMetric studentMetrics = studentMetricRepository.findByStudentId(studentId).orElse(new StudentMetric());

        int totalXp = studentMetrics.getSchoolXp() != null ? studentMetrics.getSchoolXp() : 0;
        int scholarLevel = (totalXp / 500) + 1;
        int levelProgress = (totalXp % 500) * 100 / 500;
        int xpToNextLevel = 500 - (totalXp % 500);

        List<AcademicSubmission> submissions = academicSubmissionRepository.findByStudentId(studentId);
        List<ParentReward> pendingRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "PENDING");
        List<ParentQuest> parentQuests = parentQuestRepository.findByStudentId(studentId);

        List<ParentReward> parentRewards = parentRewardRepository.findByStudentId(studentId);

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

        // All children linked to this parent, so the app can offer a switcher
        // for families with more than one child at the school.
        List<Map<String, Object>> childrenList = children.stream().map(s -> {
            Map<String, Object> c = new HashMap<>();
            c.put("id", s.getId());
            c.put("firstName", s.getFirstName());
            c.put("lastName", s.getLastName());
            if (s.getClassSection() != null) {
                c.put("gradeName", s.getClassSection().getGradeName());
                c.put("sectionName", s.getClassSection().getSectionName());
            }
            return c;
        }).collect(Collectors.toList());
        response.put("children", childrenList);

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

    @GetMapping("/bus-location")
    public ResponseEntity<?> getBusLocation(
            @RequestParam(value = "studentId", required = false) UUID studentId,
            Authentication authentication) {

        UUID resolvedId = resolveStudentId(studentId, authentication);
        if (resolvedId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No student found"));
        }

        Student student = studentRepository.findById(resolvedId).orElse(null);
        UUID busRouteId = (student != null && student.getClassSection() != null)
                ? student.getClassSection().getBusRouteId()
                : null;
        if (busRouteId == null) {
            return ResponseEntity.ok(Map.of("assigned", false));
        }

        BusRoute route = busRouteRepository.findById(busRouteId).orElse(null);
        if (route == null) {
            return ResponseEntity.ok(Map.of("assigned", false));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("assigned", true);
        response.put("routeName", route.getName());
        response.put("latitude", route.getCurrentLatitude());
        response.put("longitude", route.getCurrentLongitude());
        response.put("lastPingAt", route.getLastPingAt());
        return ResponseEntity.ok(response);
    }
}
