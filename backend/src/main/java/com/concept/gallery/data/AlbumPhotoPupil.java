package com.concept.gallery.data;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A member of staff's statement that this pupil appears in this photo (V27).
 *
 * <p>No tenant column: it belongs to its school through the photo's album, and
 * {@link AlbumPhotoPupilRepository} only reads through that join.
 */
@Entity
@Table(name = "album_photo_pupils")
public class AlbumPhotoPupil {

    @Id
    private UUID id;

    @Column(name = "photo_id", nullable = false)
    private UUID photoId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "tagged_by_user_id", nullable = false)
    private UUID taggedByUserId;

    @Column(name = "tagged_at", nullable = false)
    private LocalDateTime taggedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPhotoId() { return photoId; }
    public void setPhotoId(UUID photoId) { this.photoId = photoId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getTaggedByUserId() { return taggedByUserId; }
    public void setTaggedByUserId(UUID taggedByUserId) { this.taggedByUserId = taggedByUserId; }
    public LocalDateTime getTaggedAt() { return taggedAt; }
    public void setTaggedAt(LocalDateTime taggedAt) { this.taggedAt = taggedAt; }
}
