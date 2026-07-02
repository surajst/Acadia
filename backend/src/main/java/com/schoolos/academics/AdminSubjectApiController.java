package com.schoolos.academics;

import com.schoolos.management.ClassSectionRepository;
import com.schoolos.user.CurrentUserService;
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
    private final ClassSectionRepository classSectionRepository;

    public AdminSubjectApiController(SubjectService subjectService,
                                      CurrentUserService currentUserService,
                                      ClassSectionRepository classSectionRepository) {
        this.subjectService = subjectService;
        this.currentUserService = currentUserService;
        this.classSectionRepository = classSectionRepository;
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
    public ResponseEntity<List<Subject>> listSubjects(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.listAllSubjects(tenantId));
    }

    @PostMapping
    public ResponseEntity<?> createSubject(@RequestBody CreateSubjectDto dto, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not resolve tenant"));
        }
        Subject subject = subjectService.createSubject(tenantId, academicYearId, dto.code, dto.displayName, dto.colorHex);
        return ResponseEntity.ok(subject);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> renameSubject(@PathVariable UUID id, @RequestBody RenameSubjectDto dto) {
        return ResponseEntity.ok(subjectService.renameSubject(id, dto.displayName));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateSubject(@PathVariable UUID id) {
        subjectService.setActive(id, false);
        return ResponseEntity.ok(Map.of("status", "deactivated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateSubject(@PathVariable UUID id) {
        subjectService.setActive(id, true);
        return ResponseEntity.ok(Map.of("status", "activated"));
    }

    @GetMapping("/grades")
    public ResponseEntity<List<String>> listGrades(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<String> grades = classSectionRepository.findByTenantId(tenantId).stream()
                .map(cs -> cs.getGradeName())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        return ResponseEntity.ok(grades);
    }

    @GetMapping("/grades/{gradeName}")
    public ResponseEntity<List<Subject>> listSubjectsForGrade(@PathVariable String gradeName, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.listSubjectsForGrade(tenantId, gradeName));
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
