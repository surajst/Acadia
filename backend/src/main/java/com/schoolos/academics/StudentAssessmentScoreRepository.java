package com.schoolos.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentAssessmentScoreRepository extends JpaRepository<StudentAssessmentScore, UUID> {
    List<StudentAssessmentScore> findByStudentId(UUID studentId);
    List<StudentAssessmentScore> findByAssessmentId(UUID assessmentId);
    Optional<StudentAssessmentScore> findByStudentIdAndAssessmentId(UUID studentId, UUID assessmentId);
}
