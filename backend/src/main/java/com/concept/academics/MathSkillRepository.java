package com.concept.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MathSkillRepository extends TenantScopedRepository<MathSkill, UUID> {
    List<MathSkill> findByChapterSequenceNumber(Integer sequenceNumber);
}
