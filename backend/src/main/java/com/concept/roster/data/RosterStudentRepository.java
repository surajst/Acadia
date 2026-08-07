package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.Student;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data layer for the roster slice. Extends {@link TenantScopedRepository} so the
 * only id lookup available is tenant-scoped ({@code findByIdAndTenantId}) — the
 * application layer physically cannot fetch a student across tenants, which is
 * how the cross-tenant profile IDOR is designed out (see ADR 0001).
 */
@Repository
public interface RosterStudentRepository extends TenantScopedRepository<Student, UUID> {
}
