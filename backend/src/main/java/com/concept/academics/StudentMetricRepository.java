package com.concept.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentMetricRepository extends TenantScopedRepository<StudentMetric, UUID> {
    Optional<StudentMetric> findByStudentId(UUID studentId);
}
