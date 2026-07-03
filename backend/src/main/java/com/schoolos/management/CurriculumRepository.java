package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    List<Curriculum> findByTenantId(UUID tenantId);

    // Fetch all topics for a given syllabus, standard, and subject, ordered by topicOrder
    List<Curriculum> findByTenantIdAndSyllabusTypeAndStandardAndSubjectCodeOrderByTopicOrderAsc(
        UUID tenantId, SyllabusType syllabusType, Integer standard, String subjectCode
    );

    // Fetch all curriculum records to extract distinct subjects for a given syllabus and standard
    List<Curriculum> findByTenantIdAndSyllabusTypeAndStandard(
        UUID tenantId, SyllabusType syllabusType, Integer standard
    );

    // Fetch all curriculum records for a given syllabus and standard ordered by topicOrder
    List<Curriculum> findByTenantIdAndSyllabusTypeAndStandardOrderByTopicOrderAsc(
        UUID tenantId, SyllabusType syllabusType, Integer standard
    );
}
