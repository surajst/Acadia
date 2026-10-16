package com.concept.gallery.app;

import java.time.LocalDateTime;
import java.util.UUID;

import com.concept.gallery.data.AudienceType;

/**
 * What the gallery hands upwards. Entities stay in this layer (ADR 0001).
 */
public final class GalleryViews {

    private GalleryViews() {
    }

    /**
     * A request to create an album.
     *
     * @param consentConfirmed the member of staff has checked that every child
     *                         who will appear has photo consent. Without it the
     *                         album is refused: V26 makes the consent record
     *                         mandatory, and it must be a person's statement,
     *                         not a default.
     */
    public record CreateAlbum(String title, AudienceType audienceType, String audienceRef,
                              boolean consentConfirmed) {
    }

    /** An album, as a list shows it. {@code audienceLabel} is words, never an id. */
    public record AlbumSummary(UUID id, String title, AudienceType audienceType,
                               String audienceLabel, LocalDateTime createdAt) {
    }

    /** A pupil in an album's audience with no photo consent on record. */
    public record PupilWithoutConsent(String name, String sectionLabel) {
    }

    /**
     * A photo, as a gallery shows it. The storage keys stay behind: the read
     * path will turn them into short-lived links in this layer.
     *
     * @param withheldForConsent it shows a pupil who has no photo consent now.
     *                           Pupils and parents are never sent such a photo;
     *                           staff see it flagged, so they can act on it.
     */
    public record PhotoSummary(UUID id, Integer width, Integer height, LocalDateTime addedAt,
                               boolean withheldForConsent) {
    }

    /** One photo a pupil is tagged in, for staff answering a consent question. */
    public record PhotoOfPupil(UUID photoId, UUID albumId, String albumTitle, LocalDateTime addedAt) {
    }
}
