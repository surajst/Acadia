package com.concept.gallery.data;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One photograph in an album: where its two derivatives live, never the
 * original (see {@code MediaStore} on the media-store branch for why).
 *
 * <p>No tenant column in V26: a photo belongs to its school through its album.
 * That is why {@link AlbumPhotoRepository} offers no lookup that does not go
 * through a tenant-scoped album.
 */
@Entity
@Table(name = "album_photos")
public class AlbumPhoto {

    @Id
    private UUID id;

    @Column(name = "album_id", nullable = false)
    private UUID albumId;

    @Column(name = "thumb_key", nullable = false, length = 500)
    private String thumbKey;

    @Column(name = "display_key", nullable = false, length = 500)
    private String displayKey;

    private Integer width;

    private Integer height;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAlbumId() { return albumId; }
    public void setAlbumId(UUID albumId) { this.albumId = albumId; }
    public String getThumbKey() { return thumbKey; }
    public void setThumbKey(String thumbKey) { this.thumbKey = thumbKey; }
    public String getDisplayKey() { return displayKey; }
    public void setDisplayKey(String displayKey) { this.displayKey = displayKey; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(UUID uploadedByUserId) { this.uploadedByUserId = uploadedByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
}
