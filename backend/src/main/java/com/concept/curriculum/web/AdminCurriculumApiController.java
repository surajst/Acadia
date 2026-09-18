package com.concept.curriculum.web;

import com.concept.curriculum.app.CurriculumAdminService;
import com.concept.curriculum.app.CurriculumException;
import com.concept.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin CRUD for a school's curriculum topics — the write path this slice never
 * had. Sits under {@code /api/admin/**}, which SecurityConfig restricts to
 * ADMIN, so a teacher cannot rewrite the syllabus.
 *
 * <p>Binds the request, resolves the tenant and maps failures onto statuses;
 * every decision lives in {@link CurriculumAdminService} (ADR 0001).
 */
@RestController
@RequestMapping("/api/admin/curriculum")
public class AdminCurriculumApiController {

    private final CurriculumAdminService curriculumAdminService;
    private final TenantContext tenantContext;

    public AdminCurriculumApiController(CurriculumAdminService curriculumAdminService,
                                        TenantContext tenantContext) {
        this.curriculumAdminService = curriculumAdminService;
        this.tenantContext = tenantContext;
    }

    /** Flat request body for create and update. */
    public static class TopicRequest {
        public String subjectCode;
        public Integer standard;
        public String topicName;
        public Integer topicOrder;
        public Integer xpReward;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "standard", required = false) Integer standard) {
        return ResponseEntity.ok(curriculumAdminService.list(tenantId(), standard));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TopicRequest request) {
        return ResponseEntity.ok(curriculumAdminService.create(
                tenantId(), tenantContext.getAcademicYearId().orElse(null),
                request.subjectCode, request.standard, request.topicName,
                request.topicOrder, request.xpReward));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody TopicRequest request) {
        return ResponseEntity.ok(curriculumAdminService.update(
                id, tenantId(), request.subjectCode, request.standard,
                request.topicName, request.topicOrder, request.xpReward));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        curriculumAdminService.delete(id, tenantId());
        return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
    }

    private UUID tenantId() {
        return tenantContext.getTenantId().orElse(null);
    }

    @ExceptionHandler(CurriculumException.class)
    public ResponseEntity<?> handle(CurriculumException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}
