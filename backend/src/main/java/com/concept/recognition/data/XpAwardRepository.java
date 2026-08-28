package com.concept.recognition.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** A child's recognition history, always read within one tenant. */
@Repository
public interface XpAwardRepository extends TenantScopedRepository<XpAward, UUID> {

    List<XpAward> findTop20ByStudentIdAndTenantIdOrderByCreatedAtDesc(UUID studentId, UUID tenantId);

    /** For a parent with more than one child, in one query rather than one each. */
    List<XpAward> findByStudentIdInAndTenantIdOrderByCreatedAtDesc(
            Collection<UUID> studentIds, UUID tenantId);
}
