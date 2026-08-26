package com.concept.academics.web;

import com.concept.academics.app.SubjectService;import com.concept.academics.app.SubjectView;

import com.concept.user.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/subjects")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubjectApiController {

    private final SubjectService subjectService;
    private final CurrentUserService currentUserService;

    public AdminSubjectApiController(SubjectService subjectService,
                                      CurrentUserService currentUserService) {
        this.subjectService = subjectService;
        this.currentUserService = currentUserService;
    }

    public static class CreateSubjectDto {
        public String code;
        public String displayName;
        public String colorHex;
    }

    public static class RenameSubjectDto {
        public String displayName;
    }

    public static class AssignGradeSubjectsDto {
        public List<UUID> subjectIds;
    }

    @GetMapping
    public ResponseEntity<List<SubjectView>> listSubjects(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.allSubjectViews(tenantId));
    }

    @PostMapping
    public ResponseEntity<?> createSubject(@RequestBody CreateSubjectDto dto, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not resolve tenant"));
        }
        return ResponseEntity.ok(subjectService.createSubjectView(
                tenantId, academicYearId, dto.code, dto.displayName, dto.colorHex));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> renameSubject(@PathVariable UUID id, @RequestBody RenameSubjectDto dto, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.renameSubject(id, dto.displayName, tenantId));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateSubject(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        subjectService.setActive(id, false, tenantId);
        return ResponseEntity.ok(Map.of("status", "deactivated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateSubject(@PathVariable UUID id, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        subjectService.setActive(id, true, tenantId);
        return ResponseEntity.ok(Map.of("status", "activated"));
    }

    @GetMapping("/grades")
    public ResponseEntity<List<String>> listGrades(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.gradeNames(tenantId));
    }

    @GetMapping("/grades/{gradeName}")
    public ResponseEntity<List<SubjectView>> listSubjectsForGrade(@PathVariable String gradeName, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.subjectViewsForGrade(tenantId, gradeName));
    }

    @PutMapping("/grades/{gradeName}")
    public ResponseEntity<?> assignSubjectsToGrade(@PathVariable String gradeName,
                                                    @RequestBody AssignGradeSubjectsDto dto,
                                                    Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);
        subjectService.assignSubjectsToGrade(tenantId, academicYearId, gradeName, dto.subjectIds);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }
}
