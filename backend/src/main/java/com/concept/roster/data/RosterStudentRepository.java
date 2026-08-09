package com.concept.roster.data;

import com.concept.common.TenantScopedRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Student;
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

    /** How many students are still in a section — used to block deleting a non-empty one. */
    long countByClassSection(ClassSection classSection);
}
