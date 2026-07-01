package com.schoolos.academics;

import com.schoolos.management.ClassSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    List<Assessment> findByClassSection(ClassSection classSection);
    List<Assessment> findByClassSectionAndSubjectType(ClassSection classSection, com.schoolos.management.SubjectType subjectType);
}
