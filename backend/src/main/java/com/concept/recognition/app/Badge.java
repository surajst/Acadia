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
 * <p>The wording leans early-years because that is where the gap was -- a
 * nursery teacher previously had no way to recognise a child at all -- but
 * effort, kindness and improvement are not preschool-only ideas, so the list is
 * offered to every school rather than gated on {@code SchoolType}.
 */
public enum Badge {

    KIND_HANDS("Kind Hands", "🤝", 10, "Helped someone without being asked"),
    GREAT_LISTENING("Great Listening", "👂", 10, "Listened well at circle time"),
    TIDY_UP_STAR("Tidy-Up Star", "🧹", 10, "Cleared up carefully"),
    GREAT_SHARING("Great Sharing", "🫶", 10, "Shared with a friend"),
    BRAVE_TRY("Brave Try", "💪", 15, "Tried something hard without giving up"),
    BIG_QUESTION("Big Question", "❓", 15, "Asked a really good question"),
    GREAT_WORK("Great Work", "✏️", 15, "Lovely piece of work"),
    MOST_IMPROVED("Most Improved", "📈", 20, "Came a long way this week"),
    STAR_OF_THE_DAY("Star of the Day", "⭐", 25, "Star of the day");

    private final String label;
    private final String emoji;
    private final int points;
    private final String suggestion;

    Badge(String label, String emoji, int points, String suggestion) {
        this.label = label;
        this.emoji = emoji;
        this.points = points;
        this.suggestion = suggestion;
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
