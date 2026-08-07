package com.concept.fees.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.Student;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped student lookups for the fee ledger: the invoice-to-student
 * enrichment and the "create invoice" student picker both resolve students only
 * within the caller's tenant, so the ledger can never surface a foreign student.
 */
@Repository
public interface FeeStudentRepository extends TenantScopedRepository<Student, UUID> {

    List<Student> findByTenantId(UUID tenantId);

    List<Student> findByIdInAndTenantId(Collection<UUID> ids, UUID tenantId);
}
