package com.concept.fees.data;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeStructureRepository extends TenantScopedRepository<FeeStructure, UUID> {
    Optional<FeeStructure> findByGradeLevel(String gradeLevel);
    Optional<FeeStructure> findByTenantIdAndGradeLevel(UUID tenantId, String gradeLevel);
}
