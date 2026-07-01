package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parent")
public class ParentPortalApiController {

    private final ParentQuestRepository parentQuestRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudentProgressService studentProgressService;

    public ParentPortalApiController(ParentQuestRepository parentQuestRepository,
                                     StudentMetricRepository studentMetricRepository,
                                     StudentRepository studentRepository,
                                     ParentRepository parentRepository,
                                     AttendanceRepository attendanceRepository,
                                     StudentProgressService studentProgressService) {
        this.parentQuestRepository = parentQuestRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.attendanceRepository = attendanceRepository;
        this.studentProgressService = studentProgressService;
    }

    // ─── Resolve helpers ────────────────────────────────────────────────────
    private Parent resolveParent(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "ramesh";
        return parentRepository.findAll().stream()
                .filter(p -> username.equalsIgnoreCase(p.getFirstName())
                        || (p.getEmail() != null && p.getEmail().toLowerCase().startsWith(username.toLowerCase())))
                .findFirst()
                .orElseGet(() -> parentRepository.findAll().stream()
                        .filter(p -> "Ramesh".equals(p.getFirstName()) || "Rajesh".equals(p.getFirstName()))
                        .findFirst()
                        .orElseGet(() -> parentRepository.findAll().stream().findFirst().orElse(null)));
    }

    private Student resolveChildForParent(Parent parent) {
        if (parent == null) return null;
        List<Student> linked = studentRepository.findByParentsContaining(parent);
        if (!linked.isEmpty()) return linked.get(0);
        // fallback to Arjun
        return studentRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000091")).orElse(null);
    }

    // ─── Existing endpoints ──────────────────────────────────────────────────

    @PostMapping("/approve-quest/{id}")
    @Transactional
    public ResponseEntity<?> approveQuest(@PathVariable UUID id) {
        Optional<ParentQuest> questOpt = parentQuestRepository.findById(id);
        if (questOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Quest not found"));
        }
        ParentQuest quest = questOpt.get();
        quest.setStatus("VERIFIED");
        parentQuestRepository.save(quest);

        StudentMetric metric = studentMetricRepository.findByStudentId(quest.getStudent().getId()).orElseThrow();
        metric.setParentXp(metric.getParentXp() + quest.getXpBounty());
        studentMetricRepository.save(metric);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    public static class AssignQuestDto {
        private String title;
        private String description;
        private Integer xpReward;
        private UUID studentId;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getXpReward() { return xpReward; }
        public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
        public UUID getStudentId() { return studentId; }
        public void setStudentId(UUID studentId) { this.studentId = studentId; }
    }

    @PostMapping("/assign-quest")
    @Transactional
    public ResponseEntity<?> assignQuest(@RequestBody AssignQuestDto dto, Authentication authentication) {
        Optional<Student> studentOpt = studentRepository.findById(dto.getStudentId());
        if (studentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
        }
        Student student = studentOpt.get();
        Parent parent = resolveParent(authentication);
        if (parent == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Parent not found"));
        }

        ParentQuest quest = new ParentQuest();
        quest.setId(UUID.randomUUID());
        String taskDesc = dto.getTitle();
        if (dto.getDescription() != null && !dto.getDescription().trim().isEmpty()) {
            taskDesc += ": " + dto.getDescription();
        }
        quest.setTaskDescription(taskDesc);
        quest.setXpBounty(dto.getXpReward());
        quest.setStatus("IN_PROGRESS");
        quest.setStudent(student);
        quest.setParent(parent);
        quest.setTenantId(student.getTenantId());
        quest.setAcademicYearId(student.getAcademicYearId());
        parentQuestRepository.save(quest);

        return ResponseEntity.ok(Map.of("status", "success", "questId", quest.getId()));
    }

    // ─── Feature 3: Parent Attendance History ───────────────────────────────

    @GetMapping("/child-attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> getChildAttendance(Authentication authentication) {
        Parent parent = resolveParent(authentication);
        Student child = resolveChildForParent(parent);
        if (child == null) {
            return ResponseEntity.ok(List.of());
        }
        LocalDate start = LocalDate.now().minusDays(60);
        LocalDate end = LocalDate.now();
        List<Map<String, Object>> records = attendanceRepository
                .findByStudentAndAttendanceDateBetween(child, start, end)
                .stream()
                .sorted((a, b) -> b.getAttendanceDate().compareTo(a.getAttendanceDate()))
                .map(a -> Map.<String, Object>of(
                        "date", a.getAttendanceDate().toString(),
                        "status", a.getStatus().name(),
                        "dayOfWeek", a.getAttendanceDate().getDayOfWeek().toString()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(records);
    }

    // ─── Feature 4: Parent Syllabus Progress ────────────────────────────────

    @GetMapping("/child-syllabus")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> getChildSyllabus(Authentication authentication) {
        Parent parent = resolveParent(authentication);
        Student child = resolveChildForParent(parent);
        if (child == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(studentProgressService.getProgressByStudent(child.getId()));
    }
}
