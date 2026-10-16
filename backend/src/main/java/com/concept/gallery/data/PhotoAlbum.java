package com.concept.gallery.data;

import java.time.LocalDateTime;
import java.util.UUID;

import com.concept.common.BaseTenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A set of photographs shown to one audience.
 *
 * <p>The consent columns are NOT NULL in V26 on purpose: an album cannot exist
 * without a named member of staff having confirmed, at a recorded time, that the
 * children in it may be photographed. That is the record a school is asked for
 * when a parent says "my child should not have been in that".
 *
 * <p>Removal is {@code removedAt}, not a delete, so the record of who confirmed
 * consent outlives the album.
 */
@Entity
@Table(name = "photo_albums")
public class PhotoAlbum extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private AudienceType audienceType;

    /** See {@link AudienceType} for what this holds per type. */
    @Column(name = "audience_ref")
    private String audienceRef;

    @Column(name = "consent_checked_by_user_id", nullable = false)
    private UUID consentCheckedByUserId;

    @Column(name = "consent_checked_at", nullable = false)
    private LocalDateTime consentCheckedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public AudienceType getAudienceType() { return audienceType; }
    public void setAudienceType(AudienceType audienceType) { this.audienceType = audienceType; }
    public String getAudienceRef() { return audienceRef; }
    public void setAudienceRef(String audienceRef) { this.audienceRef = audienceRef; }
    public UUID getConsentCheckedByUserId() { return consentCheckedByUserId; }
    public void setConsentCheckedByUserId(UUID id) { this.consentCheckedByUserId = id; }
    public LocalDateTime getConsentCheckedAt() { return consentCheckedAt; }
    public void setConsentCheckedAt(LocalDateTime at) { this.consentCheckedAt = at; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) { this.createdByUserId = createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
}
