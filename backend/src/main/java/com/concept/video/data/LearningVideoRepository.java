package com.concept.video.data;

import com.concept.common.TenantScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Reads for learning videos. Every one is scoped to a tenant, and the live ones
 * exclude removed rows -- a removed video keeps its row so the views pointing at
 * it survive, which means "removed" has to be excluded everywhere rather than
 * relied on to have gone.
 */
@Repository
public interface LearningVideoRepository extends TenantScopedRepository<LearningVideo, UUID> {

    /** What one class can watch, newest first. */
    @Query("SELECT v FROM LearningVideo v WHERE v.tenantId = :tenantId"
            + " AND v.sectionId = :sectionId AND v.removedAt IS NULL"
            + " ORDER BY v.createdAt DESC")
    List<LearningVideo> liveForSection(@Param("tenantId") UUID tenantId,
                                       @Param("sectionId") UUID sectionId);

    /** What one teacher has posted, newest first, including nothing they removed. */
    @Query("SELECT v FROM LearningVideo v WHERE v.tenantId = :tenantId"
            + " AND v.createdByUserId = :userId AND v.removedAt IS NULL"
            + " ORDER BY v.createdAt DESC")
    List<LearningVideo> livePostedBy(@Param("tenantId") UUID tenantId,
                                     @Param("userId") UUID userId);

    /** The whole school's, for an admin. */
    @Query("SELECT v FROM LearningVideo v WHERE v.tenantId = :tenantId"
            + " AND v.removedAt IS NULL ORDER BY v.createdAt DESC")
    List<LearningVideo> liveForTenant(@Param("tenantId") UUID tenantId);

    /**
     * Whether this class already has this video.
     *
     * <p>Posting the same link to the same class twice is a double-click, not an
     * intention, and it would notify every child again.
     */
    @Query("SELECT COUNT(v) FROM LearningVideo v WHERE v.tenantId = :tenantId"
            + " AND v.sectionId = :sectionId AND v.youtubeId = :youtubeId AND v.removedAt IS NULL")
    long countLiveForSectionAndVideo(@Param("tenantId") UUID tenantId,
                                     @Param("sectionId") UUID sectionId,
                                     @Param("youtubeId") String youtubeId);
}
