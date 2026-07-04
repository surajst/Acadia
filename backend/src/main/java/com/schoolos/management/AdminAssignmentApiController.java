package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/assignments")
public class AdminAssignmentApiController {

    private static final String PILOT_TEACHER_EMAIL = "teacher@greenwood.com";
    private static final UUID   PILOT_SECTION_ID    = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Autowired
    private SubjectAssignmentService assignmentService;

    @Autowired
    private SubjectAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private CurrentUserService currentUserService;

    // ─── POST /assign ─────────────────────────────────────────────────────────

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Map<String, Object>> assign(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            UUID teacherId      = UUID.fromString((String) body.get("teacherId"));
            UUID classSectionId = UUID.fromString((String) body.get("classSectionId"));
            String subjectName  = (String) body.get("subjectName");
            boolean isHomeClass = Boolean.TRUE.equals(body.get("isHomeClass"));
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

            SubjectAssignment saved = assignmentService.assignSubject(
                    teacherId, classSectionId, subjectName, isHomeClass, tenantId);

            return ResponseEntity.ok(toMap(saved));
        } catch (IllegalStateException e) {
            // Duplicate assignment
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(409).body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(400).body(err);
        }
    }

    // ─── DELETE /{assignmentId} ───────────────────────────────────────────────

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<Map<String, Object>> remove(@PathVariable UUID assignmentId, Authentication authentication) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            assignmentService.removeAssignment(assignmentId, tenantId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "removed");
            resp.put("id", assignmentId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // ─── GET /teacher/{teacherId} ─────────────────────────────────────────────

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Map<String, Object>>> getByTeacher(@PathVariable UUID teacherId, Authentication authentication) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            List<SubjectAssignment> assignments = assignmentService.getAssignmentsForTeacher(teacherId, tenantId);
            List<Map<String, Object>> result = assignments.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // ─── GET /class/{classSectionId} ──────────────────────────────────────────

    @GetMapping("/class/{classSectionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Map<String, Object>>> getByClass(@PathVariable UUID classSectionId, Authentication authentication) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            List<SubjectAssignment> assignments = assignmentService.getAssignmentsForClass(classSectionId, tenantId);
            List<Map<String, Object>> result = assignments.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // ─── GET /all-teachers ────────────────────────────────────────────────────

    @GetMapping("/all-teachers")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<List<Map<String, Object>>> allTeachers(Authentication authentication) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            List<User> teachers = tenantId != null
                    ? userRepository.findByTenantIdAndRoleIn(tenantId, List.of(UserRole.TEACHER))
                    : List.of();

            List<Map<String, Object>> result = teachers.stream().map(teacher -> {
                long count = assignmentRepository.findByTeacher(teacher).size();
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id",              teacher.getId());
                entry.put("fullName",        teacher.getFullName());
                entry.put("email",           teacher.getEmail());
                entry.put("assignmentCount", count);
                return entry;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // ─── POST /seed ───────────────────────────────────────────────────────────
    // DEV ONLY - gated behind app.dev-mode flag AND ADMIN role

    @PostMapping("/seed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> seed() {
        if (!devMode) {
            return ResponseEntity.status(403).body(Map.of("error", "Seed endpoints are disabled in production"));
        }
        try {
            User teacher = userRepository.findByEmail(PILOT_TEACHER_EMAIL)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pilot teacher not found: " + PILOT_TEACHER_EMAIL));

            ClassSection section = classSectionRepository.findById(PILOT_SECTION_ID)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pilot section not found: " + PILOT_SECTION_ID));

            // Idempotent: skip if already seeded
            List<SubjectAssignment> existing = assignmentRepository.findByTeacher(teacher);
            if (!existing.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("status", "already_seeded");
                resp.put("count",  existing.size());
                return ResponseEntity.ok(resp);
            }

            SubjectAssignment assignment = assignmentService.assignSubject(
                    teacher.getId(),
                    PILOT_SECTION_ID,
                    "Mathematics",
                    true,  // isHomeClass = true
                    null   // dev-mode-only seed path, no caller tenant to scope
            );

            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "seeded");
            resp.put("count",  1);
            resp.put("assignment", toMap(assignment));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status",  "error");
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(SubjectAssignment a) {
        ClassSection section = a.getClassSection();
        User teacher = a.getTeacher();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",             a.getId());
        map.put("teacherId",      teacher != null ? teacher.getId()      : null);
        map.put("teacherName",    teacher != null ? teacher.getFullName() : null);
        map.put("classSectionId", section != null ? section.getId()      : null);
        map.put("className",      section != null
                ? section.getGradeName() + " \u2013 " + section.getSectionName()
                : null);
        map.put("subjectName",    a.getSubjectName());
        map.put("isHomeClass",    a.isHomeClass());
        return map;
    }
}
