package com.concept.user;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends TenantScopedRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByRole(UserRole role);
    long countByRoleAndTenantId(UserRole role, UUID tenantId);
    List<User> findByTenantIdAndRoleIn(UUID tenantId, List<UserRole> roles);
    List<User> findByTenantIdAndApprovalStatus(UUID tenantId, User.ApprovalStatus approvalStatus);
}
