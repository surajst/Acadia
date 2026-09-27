package com.concept.video.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One pupil finished one video.
 *
 * <p>No tenant column: a view is scoped through the video it belongs to, which is
 * tenant-scoped, exactly as AcademicSubmission is scoped through its student. It
 * is listed in LayeringArchitectureTest's exemption set with that reason.
 *
 * <p>The unique (video, student) pair is the whole mechanism behind "post it
 * again and nothing happens". The player fires ENDED every time it reaches the
 * end, which for a pupil who rewinds is more than once, so idempotence is the
 * database's promise rather than the client's.
 */
@Entity
@Table(name = "video_views",
        uniqueConstraints = @UniqueConstraint(name = "uq_video_views_video_student",
                columnNames = {"video_id", "student_id"}))
public class VideoView {

    @Id
    private UUID id;

    @Column(name = "video_id", nullable = false)
    private UUID videoId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getVideoId() { return videoId; }
    public void setVideoId(UUID videoId) { this.videoId = videoId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
