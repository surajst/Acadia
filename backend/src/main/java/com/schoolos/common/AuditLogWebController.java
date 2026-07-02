package com.schoolos.common;

import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
public class AuditLogWebController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping("/web/admin/audit-log")
    public String getAuditLogPage() {
        return "audit_log";
    }

    @GetMapping("/web/admin/audit-log/data")
    @ResponseBody
    public List<Map<String, Object>> getAuditLogData(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (tenantId == null) return Collections.emptyList();

        Page<AuditLog> entries = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size));
        return entries.stream()
                .map(e -> Map.<String, Object>of(
                        "createdAt", e.getCreatedAt().toString(),
                        "actorEmail", e.getActorEmail() != null ? e.getActorEmail() : "system",
                        "action", e.getAction(),
                        "entityType", e.getEntityType() != null ? e.getEntityType() : "",
                        "summary", e.getSummary()
                ))
                .collect(Collectors.toList());
    }
}
