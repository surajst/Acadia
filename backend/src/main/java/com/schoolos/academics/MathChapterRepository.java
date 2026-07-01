package com.schoolos.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MathChapterRepository extends JpaRepository<MathChapter, UUID> {
}
