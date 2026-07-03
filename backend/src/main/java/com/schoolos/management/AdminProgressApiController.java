package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/progress")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProgressApiController {

    private final AdminProgressService adminProgressService;
    private final CurrentUserService currentUserService;

    public AdminProgressApiController(AdminProgressService adminProgressService, CurrentUserService currentUserService) {
        this.adminProgressService = adminProgressService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/school")
    public ResponseEntity<Map<String, Object>> getSchoolWideProgress(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(adminProgressService.getSchoolWideProgress(tenantId));
    }

    @GetMapping("/class")
    public ResponseEntity<Map<String, Object>> getClassProgress(@RequestParam("standard") int standard, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(adminProgressService.getClassProgress(tenantId, standard));
    }
}
