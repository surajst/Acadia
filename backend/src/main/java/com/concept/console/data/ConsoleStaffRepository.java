package com.concept.console.data;

import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Staff counts for the admin console hub (per-role, tenant-filtered). */
@Repository
public interface ConsoleStaffRepository extends JpaRepository<User, UUID> {

    long countByRoleAndTenantId(UserRole role, UUID tenantId);
}
