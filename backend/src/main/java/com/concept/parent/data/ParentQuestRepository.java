package com.concept.parent.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ParentQuestRepository extends TenantScopedRepository<ParentQuest, UUID> {
    List<ParentQuest> findByStudentId(UUID studentId);
    List<ParentQuest> findByParentIdAndStatus(UUID parentId, String status);
    List<ParentQuest> findByStudentIdAndStatus(UUID studentId, String status);

    /**
     * Moves a quest to APPROVED only if it is still in one of {@code from}, in
     * a single statement. Returns 0 when it was not: already approved, never
     * claimed, or approved by a second request racing this one. Two requests
     * that both read "awaiting" before either wrote would otherwise both pay
     * out the XP.
     */
    @Modifying(flushAutomatically = true)
    @Query("update ParentQuest q set q.status = 'APPROVED' "
            + "where q.id = :id and q.tenantId = :tenantId and q.status in :from")
    int approveIfAwaiting(@Param("id") UUID id, @Param("tenantId") UUID tenantId,
                          @Param("from") Collection<String> from);
}
