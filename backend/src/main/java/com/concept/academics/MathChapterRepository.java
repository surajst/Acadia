package com.concept.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import com.concept.common.TenantScopedRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MathChapterRepository extends TenantScopedRepository<MathChapter, UUID> {
}
