package com.schoolos.academics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradeSubjectRepository extends JpaRepository<GradeSubject, UUID> {
    List<GradeSubject> findByTenantIdAndGradeName(UUID tenantId, String gradeName);
    void deleteByTenantIdAndGradeName(UUID tenantId, String gradeName);

    // Projects just the subject id — avoids loading GradeSubject.subject (LAZY)
    // into the persistence context, which would otherwise leave an
    // uninitialized Hibernate proxy that Jackson can't serialize even if the
    // caller re-fetches the Subject separately (same-session identity map).
    @Query("SELECT gs.subject.id FROM GradeSubject gs WHERE gs.tenantId = :tenantId AND gs.gradeName = :gradeName")
    List<UUID> findSubjectIdsByTenantIdAndGradeName(@Param("tenantId") UUID tenantId, @Param("gradeName") String gradeName);
}
