package com.concept.common;

/**
 * Reads the numeric year out of a free-text grade label.
 *
 * <p>Grade names are free text, so "Grade 6", "Class 6" and "VI-A" all have to
 * resolve to 6. Four copies of this parsing had grown across the codebase and
 * they did not agree on what to do with a label containing no digits at all:
 * two returned {@code 6}, the others {@code -1}. A school with a "Nursery",
 * "LKG" or "Prep" section therefore served those children Grade 6 tasks and the
 * Grade 6 syllabus on some screens, and nothing on others -- silently, because
 * the fallback was a catch block.
 *
 * <p>There is one answer here now, and for a label with no digits it is
 * {@link #UNKNOWN}. That value matches no row in any grade-keyed query, so a
 * child in an unnumbered class is shown nothing rather than another year's
 * work. Showing nothing is visibly wrong and gets reported; showing Grade 6
 * content to a four-year-old looks like a content bug and does not.
 */
public final class GradeLevel {

    /** No year could be read from the label. Matches no grade-keyed row. */
    public static final int UNKNOWN = -1;

    private GradeLevel() {
    }

    public static int parse(String label) {
        if (label == null) {
            return UNKNOWN;
        }
        String digits = label.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return UNKNOWN;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            // A label with more digits than an int can hold ("Grade 99999999999").
            // Nonsense either way, so it is unknown rather than a crash.
            return UNKNOWN;
        }
    }

    /** True when a parsed value can be used to look up grade-keyed content. */
    public static boolean isKnown(int standard) {
        return standard != UNKNOWN;
    }
}
