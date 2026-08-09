package com.concept.console.data;

import com.concept.shared.data.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Student counts for the admin console hub (tenant-filtered / per-classroom). */
@Repository
public interface ConsoleStudentRepository extends JpaRepository<Student, UUID> {

    long countByTenantId(UUID tenantId);

    long countBySchoolClassId(UUID schoolClassId);
}
