package com.concept.messaging.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.ClassSection;
import com.concept.management.Parent;
import com.concept.management.Student;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped student access for messaging rosters and conversation targets.
 * Resolving a student by id is tenant-scoped ({@code findByIdAndTenantId}), so a
 * conversation can never be started against another tenant's student (ADR 0001).
 */
@Repository
public interface MsgStudentRepository extends TenantScopedRepository<Student, UUID> {

    List<Student> findByParentsContaining(Parent parent);

    List<Student> findByClassSectionIn(List<ClassSection> classSections);

    List<Student> findByTenantId(UUID tenantId);
}
