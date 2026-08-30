package com.concept.shared.data;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentRepository extends TenantScopedRepository<Student, UUID> {
    // Used by Admins/Principals to get everyone in the school tenant
    List<Student> findByTenantId(UUID tenantId);
    long countByTenantId(UUID tenantId);

    /** Batch lookup for report screens that resolve many students at once. */
    List<Student> findByIdInAndTenantId(java.util.Collection<UUID> ids, UUID tenantId);

    // Used by Teachers to pull students belonging only to their assigned sections
    List<Student> findByClassSectionIn(List<ClassSection> classSections);
    Page<Student> findByClassSectionIn(List<ClassSection> classSections, Pageable pageable);

    // [FIX] Added to support class count dashboard
    long countByClassSection(ClassSection classSection);

    // Used to filter students by school class ID
    List<Student> findByClassSectionId(UUID schoolClassId);

    // Dynamic principal resolution and pagination methods
    Page<Student> findByClassSectionId(UUID schoolClassId, Pageable pageable);
    Optional<Student> findByFirstNameIgnoreCase(String firstName);
    Optional<Student> findByUserId(UUID userId);
    Optional<Student> findByTenantIdAndRollNumber(UUID tenantId, String rollNumber);
    List<Student> findByParentsContaining(Parent parent);

    // Dynamic search: filter by name substring (first or last), scoped to one tenant
    //
    // CAST(:name AS string) is load-bearing, not decoration. With a bare :name,
    // Postgres cannot infer the parameter's type when it is null, falls back to
    // bytea, and the statement dies on "function lower(bytea) does not exist"
    // (SQLState 42883) -- taking the whole transaction with it. The
    // ":name IS NULL OR" guard does not help: the statement is prepared and
    // type-checked as a whole before any branch is evaluated. H2 accepts the
    // untyped version, so this passes every local test and every Playwright run
    // and only fails against the production database.
    @Query("SELECT s FROM Student s WHERE s.tenantId = :tenantId AND " +
           "(:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    List<Student> findByNameContainingAndGrade(
        @Param("tenantId") UUID tenantId,
        @Param("name") String name,
        @Param("gradeLevel") String gradeLevel
    );

    @Query("SELECT s FROM Student s WHERE s.tenantId = :tenantId AND " +
           "(:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findByNameContainingAndGrade(
        @Param("tenantId") UUID tenantId,
        @Param("name") String name,
        @Param("gradeLevel") String gradeLevel,
        Pageable pageable
    );

    // For teachers: filter within their assigned sections
    @Query("SELECT s FROM Student s WHERE s.classSection IN :sections" +
           " AND (:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    List<Student> findByClassSectionInAndNameAndGrade(
        @Param("sections") List<ClassSection> sections,
        @Param("name") String name,
        @Param("gradeLevel") String gradeLevel
    );

    @Query("SELECT s FROM Student s WHERE s.classSection IN :sections" +
           " AND (:name IS NULL OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')) " +
           "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))" +
           " AND (:gradeLevel IS NULL OR s.classSection.gradeName = :gradeLevel)")
    Page<Student> findByClassSectionInAndNameAndGrade(
        @Param("sections") List<ClassSection> sections,
        @Param("name") String name,
        @Param("gradeLevel") String gradeLevel,
        Pageable pageable
    );
}
