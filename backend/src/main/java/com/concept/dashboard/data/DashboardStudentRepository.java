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

    /**
     * Children with nobody to ring in an emergency.
     *
     * <p>The field is optional and stays that way: a school cannot always get a
     * number on the day a child is enrolled, and refusing the enrolment over it
     * would be worse. What was missing was anybody knowing -- 30 of 52 children on
     * the pilot had no emergency number and no screen said so.
     *
     * <p>Blank counts as missing. StudentAdminService stores "" as null, but rows
     * written before that, or by the importer, can still hold one -- and an empty
     * string is not a number anyone can ring.
     */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.tenantId = :tenantId"
            + " AND (s.emergencyContactPhone IS NULL OR TRIM(s.emergencyContactPhone) = '')")
    long countMissingEmergencyContact(@Param("tenantId") UUID tenantId);

    /** The same question for a caller who only sees their own sections. */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.classSection IN :sections"
            + " AND (s.emergencyContactPhone IS NULL OR TRIM(s.emergencyContactPhone) = '')")
    long countMissingEmergencyContactIn(@Param("sections") List<ClassSection> sections);

    /**
     * Who they are, so the count leads somewhere rather than only worrying people.
     *
     * <p>Composes with name and grade, because the search box stays usable while
     * this filter is on: typing a name should search within the children who have
     * no number, not silently drop back to the whole school.
     *
     * <p>CAST(:name AS string) for the same reason as the queries below -- Hibernate
     * 6 type-checks the whole expression before any branch is evaluated, and the
     * untyped form passes on H2 and fails only against production Postgres.
     */
    @Query("SELECT s FROM Student s WHERE s.tenantId = :tenantId"
            + " AND (s.emergencyContactPhone IS NULL OR TRIM(s.emergencyContactPhone) = '')"
            + " AND (:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))"
            + " OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))"
            + " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findMissingEmergencyContact(@Param("tenantId") UUID tenantId,
                                              @Param("name") String name,
                                              @Param("gradeLevel") String gradeLevel,
                                              Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.classSection IN :sections"
            + " AND (s.emergencyContactPhone IS NULL OR TRIM(s.emergencyContactPhone) = '')"
            + " AND (:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))"
            + " OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))"
            + " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findMissingEmergencyContactIn(@Param("sections") List<ClassSection> sections,
                                                @Param("name") String name,
                                                @Param("gradeLevel") String gradeLevel,
                                                Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.tenantId = :tenantId AND " +
           "(:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findByNameContainingAndGrade(
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("gradeLevel") String gradeLevel,
            Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.classSection IN :sections" +
           " AND (:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findByClassSectionInAndNameAndGrade(
            @Param("sections") List<ClassSection> sections,
            @Param("name") String name,
            @Param("gradeLevel") String gradeLevel,
            Pageable pageable);
}
