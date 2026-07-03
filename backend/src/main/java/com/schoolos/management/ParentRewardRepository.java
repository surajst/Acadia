package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParentRewardRepository extends JpaRepository<ParentReward, UUID> {
    List<ParentReward> findByParentIdAndStatus(UUID parentId, String status);
    List<ParentReward> findByStudentIdAndStatus(UUID studentId, String status);
    List<ParentReward> findByStudentId(UUID studentId);
}
