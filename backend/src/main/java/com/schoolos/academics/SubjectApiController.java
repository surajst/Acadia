package com.schoolos.academics;

import com.schoolos.user.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only, any-authenticated-role subject list — used by teacher/student
 * screens (web and mobile) that need to render a subject picker, as opposed
 * to AdminSubjectApiController which manages the catalog (ADMIN only).
 */
@RestController
@RequestMapping("/api/subjects")
public class SubjectApiController {

    private final SubjectService subjectService;
    private final CurrentUserService currentUserService;

    public SubjectApiController(SubjectService subjectService, CurrentUserService currentUserService) {
        this.subjectService = subjectService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<Subject>> listActiveSubjects(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(subjectService.listActiveSubjects(tenantId));
    }
}
