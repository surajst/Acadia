package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, UUID> {
    List<StudentProgress> findByStudentId(UUID studentId);
    List<StudentProgress> findByStudentIdAndCompleted(UUID studentId, boolean completed);
}
