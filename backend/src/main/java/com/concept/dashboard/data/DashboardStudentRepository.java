package com.concept.dashboard.data;

import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Roster reads for the unified dashboard. Every query is scoped either to a
 * specific class, to the caller's assigned sections, or to the tenant — never
 * to "any section found anywhere" (which would leak another tenant's roster).
 */
@Repository
public interface DashboardStudentRepository extends JpaRepository<Student, UUID> {

    List<Student> findByClassSectionId(UUID schoolClassId);

    Page<Student> findByClassSectionId(UUID schoolClassId, Pageable pageable);

    Page<Student> findByClassSectionIn(List<ClassSection> classSections, Pageable pageable);

    long countByTenantId(UUID tenantId);

    @Query("SELECT s FROM Student s WHERE s.tenantId = :tenantId AND " +
           "(:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :name, '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findByNameContainingAndGrade(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("gradeLevel") String gradeLevel,
            Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.classSection IN :sections" +
           " AND (:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :name, '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findByClassSectionInAndNameAndGrade(
            @Param("sections") List<ClassSection> sections,
            @Param("name") String name,
            @Param("gradeLevel") String gradeLevel,
            Pageable pageable);
}
