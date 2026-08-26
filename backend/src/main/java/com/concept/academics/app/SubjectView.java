package com.concept.academics.app;

import com.concept.academics.data.Subject;

import java.util.UUID;

/**
 * A subject as the web layer sees it: flat, with no persistence behind it.
 *
 * <p>The controllers used to return the {@code Subject} entity itself, which
 * ties the JSON contract to the table and hands a lazy-loading object to
 * Jackson. Both have bitten this codebase before.
 */
public record SubjectView(UUID id, String code, String displayName, String colorHex,
                          boolean active, int sortOrder) {

    static SubjectView of(Subject subject) {
        return new SubjectView(subject.getId(), subject.getCode(), subject.getDisplayName(),
                subject.getColorHex(), subject.isActive(), subject.getSortOrder());
    }
}
