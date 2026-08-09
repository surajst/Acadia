package com.concept.assignment.app;

import com.concept.assignment.app.AssignmentViews.AssignmentRow;
import com.concept.assignment.app.AssignmentViews.AssignmentsPage;
import com.concept.assignment.app.AssignmentViews.SectionOption;
import com.concept.assignment.app.AssignmentViews.TeacherOption;
import com.concept.management.ClassSection;
import com.concept.management.ClassSectionRepository;
import com.concept.management.SubjectAssignment;
import com.concept.management.SubjectAssignmentRepository;
import com.concept.management.SubjectAssignmentService;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for teacher↔class subject assignments — the JSON API, the
 * admin web page model, and the dev-only pilot seed. Owns tenant resolution,
 * duplicate/validation handling, and entity→record mapping so the web layer
 * holds no entities and no rules (ADR 0001). Domain persistence still lives in
 * the shared {@link SubjectAssignmentService}, which this slice wraps.
 */
@Service
public class AssignmentService {

    private static final String PILOT_TEACHER_EMAIL = "teacher@greenwood.com";
    private static final UUID PILOT_SECTION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    private final SubjectAssignmentService assignmentService;
    private final SubjectAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ClassSectionRepository classSectionRepository;
    private final CurrentUserService currentUserService;
    private final boolean devMode;

    public AssignmentService(SubjectAssignmentService assignmentService,
                             SubjectAssignmentRepository assignmentRepository,
                             UserRepository userRepository,
                             ClassSectionRepository classSectionRepository,
                             CurrentUserService currentUserService,
                             @Value("${app.dev-mode:false}") boolean devMode) {
        this.assignmentService = assignmentService;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.classSectionRepository = classSectionRepository;
        this.currentUserService = currentUserService;
        this.devMode = devMode;
    }

    // ─── JSON API ───────────────────────────────────────────────────────────

    public Map<String, Object> assignFromBody(Map<String, Object> body, Authentication authentication) {
        UUID teacherId = UUID.fromString((String) body.get("teacherId"));
        UUID classSectionId = UUID.fromString((String) body.get("classSectionId"));
        String subjectName = (String) body.get("subjectName");
        boolean isHomeClass = Boolean.TRUE.equals(body.get("isHomeClass"));
        return toMap(assign(teacherId, classSectionId, subjectName, isHomeClass, authentication));
    }

    public Map<String, Object> remove(UUID assignmentId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        assignmentService.removeAssignment(assignmentId, tenantId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "removed");
        resp.put("id", assignmentId);
        return resp;
    }

    public List<Map<String, Object>> byTeacher(UUID teacherId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return assignmentService.getAssignmentsForTeacher(teacherId, tenantId).stream()
                .map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> byClass(UUID classSectionId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return assignmentService.getAssignmentsForClass(classSectionId, tenantId).stream()
                .map(this::toMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> allTeachers(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<User> teachers = tenantId != null
                ? userRepository.findByTenantIdAndRoleIn(tenantId, List.of(UserRole.TEACHER))
                : List.of();
        return teachers.stream().map(teacher -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", teacher.getId());
            entry.put("fullName", teacher.getFullName());
            entry.put("email", teacher.getEmail());
            entry.put("assignmentCount", (long) assignmentRepository.findByTeacher(teacher).size());
            return entry;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> seed() {
        if (!devMode) {
            throw AssignmentException.forbidden("Seed endpoints are disabled in production");
        }
        User teacher = userRepository.findByEmail(PILOT_TEACHER_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Pilot teacher not found: " + PILOT_TEACHER_EMAIL));
        classSectionRepository.findById(PILOT_SECTION_ID)
                .orElseThrow(() -> new IllegalStateException("Pilot section not found: " + PILOT_SECTION_ID));

        List<SubjectAssignment> existing = assignmentRepository.findByTeacher(teacher);
        if (!existing.isEmpty()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "already_seeded");
            resp.put("count", existing.size());
            return resp;
        }
        SubjectAssignment assignment = assignmentService.assignSubject(
                teacher.getId(), PILOT_SECTION_ID, "Mathematics", true, null);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "seeded");
        resp.put("count", 1);
        resp.put("assignment", toMap(assignment));
        return resp;
    }

    // ─── Web page ───────────────────────────────────────────────────────────

    public AssignmentsPage assignmentsPage(UUID requestedTeacherId, Authentication authentication) {
        String role = resolveRole(authentication);
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        List<User> teachers = tenantId != null
                ? userRepository.findByTenantIdAndRoleIn(tenantId, List.of(UserRole.TEACHER))
                : List.of();
        List<ClassSection> sections = tenantId != null
                ? classSectionRepository.findByTenantId(tenantId) : List.of();

        // Only honor ?teacher= if it's one of this tenant's own teachers.
        User selectedTeacher = null;
        if (requestedTeacherId != null) {
            selectedTeacher = teachers.stream()
                    .filter(t -> t.getId().equals(requestedTeacherId)).findFirst().orElse(null);
        }
        if (selectedTeacher == null && !teachers.isEmpty()) {
            selectedTeacher = teachers.get(0);
        }

        List<SubjectAssignment> assignments = selectedTeacher != null
                ? assignmentRepository.findByTeacher(selectedTeacher) : List.of();

        return new AssignmentsPage(
                role,
                teachers.stream().map(t -> new TeacherOption(t.getId(), t.getFullName())).collect(Collectors.toList()),
                sections.stream().map(s -> new SectionOption(s.getId(), s.getGradeName(), s.getSectionName())).collect(Collectors.toList()),
                assignments.stream().map(this::toRow).collect(Collectors.toList()),
                selectedTeacher != null ? selectedTeacher.getId() : null);
    }

    public void createAssignmentWeb(UUID teacherId, UUID classSectionId, String subjectName,
                                    boolean isHomeClass, Authentication authentication) {
        assign(teacherId, classSectionId, subjectName, isHomeClass, authentication);
    }

    public void removeAssignmentWeb(UUID assignmentId, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        assignmentService.removeAssignment(assignmentId, tenantId);
    }

    // ─── internals ──────────────────────────────────────────────────────────

    private SubjectAssignment assign(UUID teacherId, UUID classSectionId, String subjectName,
                                     boolean isHomeClass, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        try {
            return assignmentService.assignSubject(teacherId, classSectionId, subjectName, isHomeClass, tenantId);
        } catch (IllegalStateException e) {
            throw AssignmentException.duplicate(e.getMessage());
        } catch (RuntimeException e) {
            throw AssignmentException.badRequest(e.getMessage());
        }
    }

    private String resolveRole(Authentication authentication) {
        if (authentication == null) return "ADMIN";
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("ADMIN");
    }

    private AssignmentRow toRow(SubjectAssignment a) {
        ClassSection section = a.getClassSection();
        String className = section != null
                ? section.getGradeName() + " – " + section.getSectionName() : "";
        return new AssignmentRow(a.getId(), className, a.getSubjectName(), a.isHomeClass());
    }

    private Map<String, Object> toMap(SubjectAssignment a) {
        ClassSection section = a.getClassSection();
        User teacher = a.getTeacher();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("teacherId", teacher != null ? teacher.getId() : null);
        map.put("teacherName", teacher != null ? teacher.getFullName() : null);
        map.put("classSectionId", section != null ? section.getId() : null);
        map.put("className", section != null
                ? section.getGradeName() + " – " + section.getSectionName() : null);
        map.put("subjectName", a.getSubjectName());
        map.put("isHomeClass", a.isHomeClass());
        return map;
    }
}
