package com.concept.gallery.data;

/**
 * Who an album is for. Matches the check constraint on
 * {@code photo_albums.audience_type} (V26).
 */
public enum AudienceType {
    /** Everyone in the school. {@code audience_ref} is empty. */
    SCHOOL,
    /** Every section of one grade. {@code audience_ref} is the grade name, e.g. "Grade 6". */
    GRADE,
    /** One class section. {@code audience_ref} is the section's id. */
    SECTION
}
