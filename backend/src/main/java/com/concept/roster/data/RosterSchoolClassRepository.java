package com.concept.roster.data;

import com.concept.management.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Persists classrooms (SchoolClass) created from the admin console. */
@Repository
public interface RosterSchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
}
