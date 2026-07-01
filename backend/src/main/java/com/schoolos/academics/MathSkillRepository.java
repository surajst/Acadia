package com.schoolos.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MathSkillRepository extends JpaRepository<MathSkill, UUID> {
    List<MathSkill> findByChapterSequenceNumber(Integer sequenceNumber);
}
