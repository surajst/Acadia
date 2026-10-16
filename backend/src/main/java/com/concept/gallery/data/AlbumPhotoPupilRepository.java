package com.concept.gallery.data;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Who is in which photo, reachable only through an album in the caller's school.
 *
 * <p>Bare Spring Data {@link org.springframework.data.repository.Repository}, as
 * for {@link AlbumPhotoRepository}: no inherited {@code findById}, and every read
 * joins photo and album and filters on the album's tenant.
 */
@org.springframework.stereotype.Repository
public interface AlbumPhotoPupilRepository
        extends org.springframework.data.repository.Repository<AlbumPhotoPupil, UUID> {

    AlbumPhotoPupil save(AlbumPhotoPupil tag);

    /** The tags on these photos, if the photos are in this school. */
    @Query("SELECT t FROM AlbumPhotoPupil t, AlbumPhoto p, PhotoAlbum a"
            + " WHERE t.photoId = p.id AND p.albumId = a.id AND a.tenantId = :tenantId"
            + " AND p.id IN :photoIds")
    List<AlbumPhotoPupil> onPhotos(@Param("photoIds") Collection<UUID> photoIds,
                                   @Param("tenantId") UUID tenantId);

    /** The live photos one pupil is tagged in, newest first, within this school. */
    @Query("SELECT p FROM AlbumPhoto p, AlbumPhotoPupil t, PhotoAlbum a"
            + " WHERE t.photoId = p.id AND p.albumId = a.id AND a.tenantId = :tenantId"
            + " AND t.studentId = :studentId AND p.removedAt IS NULL AND a.removedAt IS NULL"
            + " ORDER BY p.createdAt DESC")
    List<AlbumPhoto> photosOf(@Param("studentId") UUID studentId, @Param("tenantId") UUID tenantId);
}
