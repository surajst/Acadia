package com.concept.academics;

import com.concept.shared.data.ClassSection;
import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends TenantScopedRepository<Assessment, UUID> {
    List<Assessment> findByClassSection(ClassSection classSection);
    List<Assessment> findByClassSectionAndSubjectCode(ClassSection classSection, String subjectCode);
}
