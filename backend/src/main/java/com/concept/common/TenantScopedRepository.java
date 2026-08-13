package com.concept.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

/**
 * Base repository for entities extending {@link BaseTenantEntity}. Declares
 * the tenant-scoped lookup every caller must use instead of the inherited
 * findById(ID), which returns a row from any school regardless of the
 * caller's tenant. LayeringArchitectureTest enforces this: it bans plain
 * findById calls on any repository whose entity extends BaseTenantEntity.
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends BaseTenantEntity, ID> extends JpaRepository<T, ID> {

    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);
}
