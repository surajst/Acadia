package com.concept.transport.admin.data;

import com.concept.common.TenantScopedRepository;
import com.concept.user.User;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Tenant-scoped staff lookup for resolving the driver during route assignment.
 * Scoped so a driver from another school can never be assigned (ADR 0001).
 */
@Repository
public interface AdminBusStaffRepository extends TenantScopedRepository<User, UUID> {
}
