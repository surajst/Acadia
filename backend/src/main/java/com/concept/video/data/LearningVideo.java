package com.concept.video.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A YouTube video a teacher has pointed one class at.
 *
 * <p>Only the eleven-character id is stored, never the pasted URL -- see
 * {@code YouTubeUrls} for why. The title and thumbnail come from YouTube's oEmbed
 * endpoint at save time rather than from the teacher, so a class list cannot be
 * given a misleading name, and the same call is what proves the video exists and
 * allows embedding.
 *
 * <p>Removal is {@code removedAt}, not a delete: rows in video_views point here,
 * and taking a video off a list should not take the record of who watched it.
 */
@Entity
@Table(name = "learning_videos")
public class LearningVideo extends BaseTenantEntity {

    @Id
    private UUID id;

    /** One class section. Not a grade -- that is the mistake V20 had to undo for tasks. */
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_code", nullable = false)
    private String subjectCode;

    @Column(name = "youtube_id", nullable = false, length = 11)
    private String youtubeId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    /** The teacher's own words: "Watch before Friday's class". */
    @Column(length = 1000)
    private String note;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSectionId() { return sectionId; }
    public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getYoutubeId() { return youtubeId; }
    public void setYoutubeId(String youtubeId) { this.youtubeId = youtubeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }

    public boolean isRemoved() { return removedAt != null; }
}
