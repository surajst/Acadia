package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.user.User;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Tenant-scoped login access for provisioning and resetting student/guardian
 * logins. Resolving an existing login by id is tenant-scoped
 * ({@code findByIdAndTenantId}), so a reset can never reach across tenants;
 * {@code existsByEmail} guards username collisions (usernames are global).
 */
@Repository
public interface RosterUserRepository extends TenantScopedRepository<User, UUID> {

    boolean existsByEmail(String email);
}
