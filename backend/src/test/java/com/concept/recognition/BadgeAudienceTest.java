package com.concept.recognition;

import com.concept.recognition.app.Badge;
import com.concept.tenant.SchoolType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A secondary school was offered "Tidy-Up Star" and "Listened well at circle
 * time" as the things a teacher could recognise a Grade 11 student for, which
 * makes the whole feature read as built for somebody else's school.
 */
class BadgeAudienceTest {

    @Test
    void aSecondarySchoolIsNotOfferedNurseryBadges() {
        List<Badge> offered = Badge.forSchoolType(SchoolType.SECONDARY);

        assertFalse(offered.contains(Badge.TIDY_UP_STAR), "the reported case");
        assertFalse(offered.contains(Badge.GREAT_LISTENING));
        assertTrue(offered.contains(Badge.TOOK_THE_LEAD));
        assertTrue(offered.contains(Badge.GREAT_WORK), "effort is not age-specific");
    }

    @Test
    void aPreschoolIsNotOfferedSeniorBadges() {
        List<Badge> offered = Badge.forSchoolType(SchoolType.PRESCHOOL);

        assertFalse(offered.contains(Badge.TOOK_THE_LEAD));
        assertTrue(offered.contains(Badge.TIDY_UP_STAR));
        assertTrue(offered.contains(Badge.GREAT_WORK));
    }

    /** Primary keeps the early-years wording; "Cleared up carefully" still lands. */
    @Test
    void primaryKeepsTheEarlyYearsWording() {
        assertTrue(Badge.forSchoolType(SchoolType.PRIMARY).contains(Badge.TIDY_UP_STAR));
    }

    /** K10 spans Nursery to Class 10, so it genuinely needs both sets. */
    @Test
    void aSchoolSpanningBothAgesGetsEverything() {
        assertEqualsSize(Badge.values().length, Badge.forSchoolType(SchoolType.K10));
        assertEqualsSize(Badge.values().length, Badge.forSchoolType(null));
    }

    private void assertEqualsSize(int expected, List<Badge> actual) {
        assertTrue(actual.size() == expected,
                "expected every badge (" + expected + "), got " + actual.size());
    }

    /**
     * Filtering the picker must not filter history. A child recognised before
     * their school changed type still has to render.
     */
    @Test
    void everyBadgeStillResolvesByCodeWhicheverSchoolTypeIsSet() {
        for (Badge badge : Badge.values()) {
            assertTrue(Badge.byCode(badge.getCode()).isPresent(),
                    badge.getCode() + " must still resolve, or a past award renders blank");
        }
    }
}
