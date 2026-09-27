package com.concept.video.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who has watched what.
 *
 * <p>Every finder goes through a video id the caller has already resolved within
 * their own tenant, or through a student id the caller has already been proved
 * entitled to. VideoView carries no tenant column of its own -- it is scoped
 * through its video, the same way AcademicSubmission is scoped through its
 * student -- so the scoping has to happen before these are called.
 */
@Repository
public interface VideoViewRepository extends JpaRepository<VideoView, UUID> {

    Optional<VideoView> findByVideoIdAndStudentId(UUID videoId, UUID studentId);

    /** Which of these videos this pupil has finished, for painting "Watched" on a list. */
    @Query("SELECT v.videoId FROM VideoView v WHERE v.studentId = :studentId"
            + " AND v.videoId IN :videoIds")
    List<UUID> watchedAmong(@Param("studentId") UUID studentId,
                            @Param("videoIds") List<UUID> videoIds);

    long countByVideoId(UUID videoId);
}
