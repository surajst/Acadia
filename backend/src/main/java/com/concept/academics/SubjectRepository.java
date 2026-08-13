package com.concept.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectRepository extends TenantScopedRepository<Subject, UUID> {
    List<Subject> findByTenantIdOrderBySortOrderAsc(UUID tenantId);
    List<Subject> findByTenantIdAndActiveTrueOrderBySortOrderAsc(UUID tenantId);
    Optional<Subject> findByTenantIdAndCode(UUID tenantId, String code);
    long countByTenantId(UUID tenantId);
}
