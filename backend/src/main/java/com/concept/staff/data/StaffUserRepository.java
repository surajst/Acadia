package com.concept.staff.data;

import com.concept.common.TenantScopedRepository;
import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped staff (User) access for the admin staff console. Listing is
 * always filtered by the acting tenant, and {@code existsByEmail} guards the
 * global username namespace on invite (ADR 0001).
 */
@Repository
public interface StaffUserRepository extends TenantScopedRepository<User, UUID> {

    List<User> findByTenantIdAndRoleIn(UUID tenantId, List<UserRole> roles);

    boolean existsByEmail(String email);
}
