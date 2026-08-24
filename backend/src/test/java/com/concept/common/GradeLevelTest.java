package com.concept.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grade labels are free text, and four copies of this parsing disagreed about
 * what a label with no digits means -- two answered 6, two answered -1. A
 * school with a "Nursery" or "Prep" section got Grade 6 work on some screens
 * and nothing on others.
 */
class GradeLevelTest {

    @Test
    void readsTheYearOutOfTheUsualLabels() {
        assertEquals(6, GradeLevel.parse("Grade 6"));
        assertEquals(6, GradeLevel.parse("Class 6"));
        assertEquals(6, GradeLevel.parse("6"));
        assertEquals(6, GradeLevel.parse("6-A"));
        assertEquals(12, GradeLevel.parse("Grade 12"));
        assertEquals(1, GradeLevel.parse("Std 1"));
    }

    @Test
    void aLabelWithNoDigitsIsUnknownRatherThanGradeSix() {
        // The bug this exists to prevent: "Nursery" strips to "", parseInt
        // threw, and the catch block answered 6 -- so a four-year-old was
        // served Grade 6 tasks and the Grade 6 syllabus.
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("Nursery"));
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("LKG"));
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("UKG"));
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("Prep"));
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("Reception"));
    }

    @Test
    void unknownMatchesNoRealGrade() {
        // The whole safety argument rests on this: UNKNOWN is used directly as
        // a query parameter, so it must not collide with a grade a school could
        // plausibly name.
        assertTrue(GradeLevel.UNKNOWN < 0);
        assertFalse(GradeLevel.isKnown(GradeLevel.UNKNOWN));
        assertTrue(GradeLevel.isKnown(0), "a nursery could legitimately be 'Grade 0'");
        assertTrue(GradeLevel.isKnown(1));
    }

    @Test
    void handlesNullAndEmptyWithoutThrowing() {
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse(null));
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse(""));
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("   "));
    }

    @Test
    void aLabelTooLongToBeANumberIsUnknownNotACrash() {
        assertEquals(GradeLevel.UNKNOWN, GradeLevel.parse("Grade 99999999999999"));
    }

    @Test
    void digitsAnywhereInTheLabelAreCollected() {
        // Documenting the existing behaviour rather than endorsing it: the
        // parser concatenates every digit, so a label mixing a year with a room
        // number reads as neither. Worth knowing before naming sections.
        assertEquals(62, GradeLevel.parse("Grade 6 Room 2"));
    }
}
