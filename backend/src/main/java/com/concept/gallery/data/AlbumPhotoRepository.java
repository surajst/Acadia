package com.concept.gallery.data;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Photos, reachable only through an album in the caller's school.
 *
 * <p>Extends Spring's bare {@link org.springframework.data.repository.Repository}
 * rather than JpaRepository, so there is no inherited {@code findById}: a photo
 * has no tenant column of its own, and a lookup by id alone would hand one
 * school's photograph to another. Every read joins the album and filters on its
 * tenant.
 */
@org.springframework.stereotype.Repository
public interface AlbumPhotoRepository extends org.springframework.data.repository.Repository<AlbumPhoto, UUID> {

    AlbumPhoto save(AlbumPhoto photo);

    /** The live photos of one live album, oldest first, if the album is in this school. */
    @Query("SELECT p FROM AlbumPhoto p, PhotoAlbum a WHERE p.albumId = a.id"
            + " AND a.id = :albumId AND a.tenantId = :tenantId"
            + " AND a.removedAt IS NULL AND p.removedAt IS NULL ORDER BY p.createdAt ASC")
    List<AlbumPhoto> liveInAlbum(@Param("albumId") UUID albumId, @Param("tenantId") UUID tenantId);

    /** One live photo in a live album of this school. */
    @Query("SELECT p FROM AlbumPhoto p, PhotoAlbum a WHERE p.albumId = a.id"
            + " AND p.id = :photoId AND a.tenantId = :tenantId"
            + " AND a.removedAt IS NULL AND p.removedAt IS NULL")
    java.util.Optional<AlbumPhoto> liveByIdInTenant(@Param("photoId") UUID photoId,
                                                    @Param("tenantId") UUID tenantId);
}
