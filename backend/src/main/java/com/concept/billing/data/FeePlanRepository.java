package com.concept.billing.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeePlanRepository extends TenantScopedRepository<FeePlan, UUID> {

    Optional<FeePlan> findByTenantIdAndAcademicYearIdAndGradeLevel(
            UUID tenantId, UUID academicYearId, String gradeLevel);

    List<FeePlan> findByTenantIdAndAcademicYearIdOrderByGradeLevelAsc(UUID tenantId, UUID academicYearId);
}
