package com.concept.fees.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeStructureRepository extends TenantScopedRepository<FeeStructure, UUID> {

    /**
     * The lookup invoicing depends on. Scoped to the year as well as the school
     * because a school that has rolled over holds two rows for the same grade.
     */
    Optional<FeeStructure> findByTenantIdAndAcademicYearIdAndGradeLevel(
            UUID tenantId, UUID academicYearId, String gradeLevel);

    List<FeeStructure> findByTenantIdAndAcademicYearIdOrderByGradeLevelAsc(
            UUID tenantId, UUID academicYearId);

    /**
     * Retained for the invoice path, which prices from the student's own
     * academic year rather than the school's current one.
     */
    Optional<FeeStructure> findByTenantIdAndGradeLevel(UUID tenantId, String gradeLevel);
}
