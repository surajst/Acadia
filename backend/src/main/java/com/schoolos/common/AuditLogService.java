package com.schoolos.common;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * Normal case — resolves the actor from the authenticated request via
     * the same CurrentUserService every write path since Sprint 1 already uses.
     */
    public void log(Authentication authentication, String action, String entityType, UUID entityId, String summary) {
        User user = currentUserService.getCurrentUser(authentication).orElse(null);
        UUID tenantId = user != null ? user.getTenantId() : null;
        UUID academicYearId = user != null ? user.getAcademicYearId() : null;
        UUID actorUserId = user != null ? user.getId() : null;
        String actorEmail = user != null ? user.getEmail() : null;

        logDirect(tenantId, academicYearId, actorUserId, actorEmail, action, entityType, entityId, summary);
    }

    /**
     * For write paths that run before any Authentication exists — currently
     * only TenantOnboardingService.createSchool(), the public unauthenticated
     * signup endpoint. The actor is the admin record just created in the
     * same transaction, passed directly.
     */
    public void logDirect(UUID tenantId, UUID academicYearId, UUID actorUserId, String actorEmail,
                           String action, String entityType, UUID entityId, String summary) {
        if (tenantId == null || academicYearId == null) {
            // Nothing meaningful to scope this row to — skip rather than
            // write a cross-tenant-unsafe row.
            return;
        }

        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(tenantId);
        entry.setAcademicYearId(academicYearId);
        entry.setActorUserId(actorUserId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setSummary(summary);
        auditLogRepository.save(entry);
    }
}
