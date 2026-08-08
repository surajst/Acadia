package com.concept.messaging.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.Parent;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped guardian access for resolving the acting parent in messaging. */
@Repository
public interface MsgParentRepository extends TenantScopedRepository<Parent, UUID> {

    Optional<Parent> findByUserId(UUID userId);
}
