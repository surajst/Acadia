package com.concept.gallery.data;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.concept.common.TenantScopedRepository;

/**
 * Reads for albums, every one scoped to a school. "Live" excludes removed
 * albums, which keep their row for the consent record.
 */
@Repository
public interface PhotoAlbumRepository extends TenantScopedRepository<PhotoAlbum, UUID> {

    @Query("SELECT a FROM PhotoAlbum a WHERE a.tenantId = :tenantId"
            + " AND a.removedAt IS NULL ORDER BY a.createdAt DESC")
    List<PhotoAlbum> liveForTenant(@Param("tenantId") UUID tenantId);
}
