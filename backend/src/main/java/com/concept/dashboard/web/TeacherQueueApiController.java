package com.concept.dashboard.web;

import com.concept.dashboard.app.DashboardService;
import com.concept.dashboard.app.TeacherDashboardView;
import com.concept.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON binding for the teacher verification queues (pending milestone
 * submissions and pending syllabus progress), for the mobile app. Reuses the
 * same {@link DashboardService#buildTeacherQueues} the web dashboard renders
 * from (ADR 0001) -- one queue, two surfaces.
 */
@RestController
@RequestMapping("/api/teacher/queue")
public class TeacherQueueApiController {

    private final DashboardService dashboardService;
    private final TenantContext tenantContext;

    public TeacherQueueApiController(DashboardService dashboardService, TenantContext tenantContext) {
        this.dashboardService = dashboardService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<TeacherDashboardView> pending(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        return ResponseEntity.ok(dashboardService.buildTeacherQueues(email, "TEACHER", tenantContext.getTenantId().orElse(null)));
    }
}
