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

    /**
     * An album, as a list shows it. {@code audienceLabel} is words, never an id.
     *
     * @param photoCount the photos this person would see in it
     * @param coverUrl   a short-lived link to the newest of those, or null
     * @param canAddPhotos whether this person may upload to it and remove from it
     */
    public record AlbumSummary(UUID id, String title, AudienceType audienceType,
                               String audienceLabel, LocalDateTime createdAt,
                               int photoCount, String coverUrl, boolean canAddPhotos) {
    }

    /**
     * Someone an album can be made for, by this person: words to show, and the
     * type and reference to send back. {@code ref} is a section id, a grade name,
     * or null for the whole school -- sent back, never shown.
     */
    public record AudienceOption(String type, String ref, String label) {
    }

    /** Whether photos can be added at all, and if not, what to tell people. */
    public record GalleryStatus(boolean available, String message) {
    }

    /** A pupil who may be tagged in an album's photos: in its audience, with consent. */
    public record TaggablePupil(UUID id, String name, String sectionLabel) {
    }

    /** A pupil in an album's audience with no photo consent on record. */
    public record PupilWithoutConsent(String name, String sectionLabel) {
    }

    /**
     * A photo, as a gallery shows it. The storage keys stay behind; what leaves
     * this layer is a pair of short-lived links.
     *
     * @param withheldForConsent it shows a pupil who has no photo consent now.
     *                           Pupils and parents are never sent such a photo;
     *                           staff see it flagged, so they can act on it.
     * @param thumbUrl   the grid-sized image, valid for a few minutes
     * @param displayUrl the full-screen image, valid for a few minutes
     * @param taggedNames the pupils tagged in it, for staff only (empty otherwise)
     */
    public record PhotoSummary(UUID id, Integer width, Integer height, LocalDateTime addedAt,
                               boolean withheldForConsent, String thumbUrl, String displayUrl,
                               java.util.List<String> taggedNames) {
    }

    /** One photo a pupil is tagged in, for staff answering a consent question. */
    public record PhotoOfPupil(UUID photoId, UUID albumId, String albumTitle, LocalDateTime addedAt) {
    }
}
