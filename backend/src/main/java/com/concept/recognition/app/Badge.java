package com.concept.recognition.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a teacher can recognise a child for, and what it is worth.
 *
 * <p>A fixed list rather than a free-text award with a number the teacher
 * picks. Two reasons: a parent comparing notes with another parent should find
 * the same words meant the same thing, and a points field a teacher types into
 * turns into an arms race between classrooms by the second term.
 *
 * <p>The wording originally leaned early-years, because that is where the gap
 * was: a nursery teacher had no way to recognise a child at all. Effort,
 * kindness and improvement are not preschool-only ideas and those badges are
 * still offered everywhere -- but "Tidy-Up Star" and "Listened well at circle
 * time" in front of a Grade 11 teacher made the whole feature read as built
 * for somebody else's school. Each badge now says who it is for.
 */
public enum Badge {

    KIND_HANDS("Kind Hands", "🤝", 10, "Helped someone without being asked", Audience.EARLY_YEARS),
    GREAT_LISTENING("Great Listening", "👂", 10, "Listened well at circle time", Audience.EARLY_YEARS),
    TIDY_UP_STAR("Tidy-Up Star", "🧹", 10, "Cleared up carefully", Audience.EARLY_YEARS),
    GREAT_SHARING("Great Sharing", "🫶", 10, "Shared with a friend", Audience.EARLY_YEARS),

    BRAVE_TRY("Brave Try", "💪", 15, "Tried something hard without giving up", Audience.ANY),
    BIG_QUESTION("Big Question", "❓", 15, "Asked a really good question", Audience.ANY),
    GREAT_WORK("Great Work", "✏️", 15, "Lovely piece of work", Audience.ANY),
    MOST_IMPROVED("Most Improved", "📈", 20, "Came a long way this week", Audience.ANY),
    STAR_OF_THE_DAY("Star of the Day", "⭐", 25, "Star of the day", Audience.ANY),

    HELPED_A_CLASSMATE("Helped a Classmate", "🤝", 10, "Explained something to someone who was stuck", Audience.SENIOR),
    THOROUGH_WORK("Thorough Work", "📐", 15, "Worked carefully and showed the method", Audience.SENIOR),
    SPOKE_UP("Spoke Up", "🗣️", 15, "Made a point well in discussion", Audience.SENIOR),
    TOOK_THE_LEAD("Took the Lead", "🧭", 20, "Led a group without being asked", Audience.SENIOR),
    STUCK_WITH_IT("Stuck With It", "🔁", 20, "Kept going after a setback", Audience.SENIOR);

    /** Who a badge is worded for. ANY reads naturally at every age. */
    public enum Audience { EARLY_YEARS, ANY, SENIOR }

    private final String label;
    private final String emoji;
    private final int points;
    private final String suggestion;
    private final Audience audience;

    Badge(String label, String emoji, int points, String suggestion, Audience audience) {
        this.label = label;
        this.emoji = emoji;
        this.points = points;
        this.suggestion = suggestion;
        this.audience = audience;
    }

    public Audience getAudience() {
        return audience;
    }

    /**
     * The badges worth offering this school.
     *
     * <p>Filtering the picker, not the data: {@link #byCode} still resolves
     * every badge, so a child's history renders whatever their school type is
     * now and whatever it was when the award was made.
     */
    public static java.util.List<Badge> forSchoolType(com.concept.tenant.SchoolType schoolType) {
        // A null school type predates the column, and K10 genuinely spans
        // Nursery to Class 10 -- both get everything. A badge that does not
        // quite fit is a smaller problem than a teacher with nothing to give.
        if (schoolType == null || schoolType == com.concept.tenant.SchoolType.K10) {
            return Arrays.asList(values());
        }
        Audience excluded = switch (schoolType) {
            // Primary keeps the early-years wording: "Cleared up carefully"
            // still lands in Class 3, and "Led a group" mostly does not.
            case PRESCHOOL, PRIMARY -> Audience.SENIOR;
            default -> Audience.EARLY_YEARS;
        };
        return Arrays.stream(values()).filter(b -> b.audience != excluded).collect(java.util.stream.Collectors.toList());
    }

    public String getLabel() {
        return label;
    }

    public String getEmoji() {
        return emoji;
    }

    public int getPoints() {
        return points;
    }

    /** Prefilled wording, so recognising a child is one tap and not an essay. */
    public String getSuggestion() {
        return suggestion;
    }

    public String getCode() {
        return name();
    }

    /**
     * Unknown codes come back empty rather than throwing.
     *
     * <p>Rows written before a badge is renamed still have to render: a child's
     * history is not something to lose because the catalogue moved on.
     */
    public static Optional<Badge> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(b -> b.name().equalsIgnoreCase(code.trim())).findFirst();
    }
}
