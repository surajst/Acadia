package com.schoolos.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

/**
 * Base repository for any tenant-owned entity. It deliberately re-exposes the
 * common lookups in a tenant-scoped form and expects concrete repositories to
 * prefer these over the inherited, tenant-blind {@code findById}/{@code existsById}.
 *
 * <p>The layering ADR (docs/adr/0001) bans bare {@code findById(id)} in the
 * application layer precisely because it is how cross-tenant IDOR leaks keep
 * happening. Extend this and call {@link #findByIdAndTenantId} instead.
 *
 * <p>Spring Data derives the queries from the method names, so the entity
 * {@code T} must have {@code id} and {@code tenantId} fields.
 */
@NoRepositoryBean
public interface TenantScopedRepository<T, ID> extends JpaRepository<T, ID> {

    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);

    boolean existsByIdAndTenantId(ID id, UUID tenantId);
}
