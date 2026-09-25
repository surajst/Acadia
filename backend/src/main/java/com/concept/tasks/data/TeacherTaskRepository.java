package com.concept.tasks.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeacherTaskRepository extends JpaRepository<TeacherTask, UUID> {
    List<TeacherTask> findByCreatedByTeacherIdAndTenantId(UUID teacherId, UUID tenantId);
    /**
     * Class tasks a student in one section should see: the ones set for their
     * own section, plus the ones with no section recorded at all.
     *
     * <p>A null class_section_id means grade-wide, which is what every task
     * raised before the column existed is. Excluding those would take tasks
     * away from children already working on them; including a task set for
     * another section is the bug this replaces. Written out as a query because
     * "equals this, or is null" is not expressible in a derived method name.
     */
    @Query("select t from TeacherTask t where t.standard = :standard "
            + "and t.assignedToClass = true and t.tenantId = :tenantId "
            + "and (t.classSectionId = :sectionId or t.classSectionId is null)")
    List<TeacherTask> findClassTasksForSection(@Param("standard") Integer standard,
                                               @Param("sectionId") UUID sectionId,
                                               @Param("tenantId") UUID tenantId);

    /**
     * Every class task for a grade, section or not. Still needed for a student
     * who is in no section: there is nothing to match against, so the old
     * grade-wide reach is the only answer available.
     */
    List<TeacherTask> findByStandardAndAssignedToClassTrueAndTenantId(Integer standard, UUID tenantId);
    List<TeacherTask> findByStudentIdAndTenantId(UUID studentId, UUID tenantId);
    List<TeacherTask> findByTenantId(UUID tenantId);

    // TeacherTask keeps its tenant columns nullable (see the entity), so it
    // cannot extend BaseTenantEntity and the ArchUnit bare-findById rule does
    // not reach it. This is the scoped lookup callers must use regardless.
    Optional<TeacherTask> findByIdAndTenantId(UUID id, UUID tenantId);
}
