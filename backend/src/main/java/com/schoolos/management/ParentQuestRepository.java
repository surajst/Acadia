package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParentQuestRepository extends JpaRepository<ParentQuest, UUID> {
    List<ParentQuest> findByStudentId(UUID studentId);
    List<ParentQuest> findByParentIdAndStatus(UUID parentId, String status);
    List<ParentQuest> findByStudentIdAndStatus(UUID studentId, String status);
}
