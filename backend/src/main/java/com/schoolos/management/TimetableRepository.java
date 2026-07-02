package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TimetableRepository extends JpaRepository<TimetableEntry, UUID> {

    List<TimetableEntry> findByTeacherIdAndDayOfWeekOrderByPeriodNumber(UUID teacherId, String dayOfWeek);

    List<TimetableEntry> findByTeacherId(UUID teacherId);

    List<TimetableEntry> findByTenantId(UUID tenantId);

    List<TimetableEntry> findByClassSectionId(UUID classSectionId);
}
