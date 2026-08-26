package com.concept.tenant;

/**
 * What kind of school this is, which decides the words the app uses and the
 * modules it shows.
 *
 * <p>Distinct from {@link TenantTier}: tier is what the school bought, type is
 * what the school <em>is</em>. A preschool on the full tier still should not be
 * shown a CBSE syllabus or asked to set homework quests.
 *
 * <p>Deliberately not a schema change beyond one column. A preschool's
 * Level -> Section -> Activity -> Teacher is the same tree as a secondary
 * school's Class -> Section -> Subject -> Teacher, so it uses the same
 * ClassSection and Subject tables. Only the labels and the visible modules
 * differ -- which is why this is an enum and not a parallel data model.
 */
public enum SchoolType {

    /** Pre-Nursery through UKG. No syllabus, no marks, no homework. */
    PRESCHOOL("Level", "Levels", "Activity Area", "Activity Areas"),

    /** Roughly Class 1-5. */
    PRIMARY("Class", "Classes", "Subject", "Subjects"),

    /** Roughly Class 6-12. */
    SECONDARY("Class", "Classes", "Subject", "Subjects"),

    /** A single school spanning kindergarten to Class 10. */
    K10("Class", "Classes", "Subject", "Subjects");

    private final String levelSingular;
    private final String levelPlural;
    private final String activitySingular;
    private final String activityPlural;

    SchoolType(String levelSingular, String levelPlural,
               String activitySingular, String activityPlural) {
        this.levelSingular = levelSingular;
        this.levelPlural = levelPlural;
        this.activitySingular = activitySingular;
        this.activityPlural = activityPlural;
    }

    /** "Class" for a secondary school, "Level" for a preschool. */
    public String getLevelSingular() {
        return levelSingular;
    }

    public String getLevelPlural() {
        return levelPlural;
    }

    /** "Subject" for a secondary school, "Activity Area" for a preschool. */
    public String getActivitySingular() {
        return activitySingular;
    }

    public String getActivityPlural() {
        return activityPlural;
    }

    /**
     * Whether children are expected to be set work and hand it in.
     *
     * <p>False for a preschool: the XP, quest and milestone-submission model
     * assumes a child who can read a task and write up what they did. Showing a
     * parent of a three-year-old an "Assign Home Quest" button is not a missing
     * translation, it is the wrong product.
     */
    public boolean hasHomeworkAndQuests() {
        return this != PRESCHOOL;
    }

    /** Whether a graded, topic-by-topic syllabus applies. */
    public boolean hasSyllabus() {
        return this != PRESCHOOL;
    }

    /** Whether children are assessed with marks rather than observations. */
    public boolean hasExamScores() {
        return this != PRESCHOOL;
    }

    /**
     * Whether levels carry a year number. Preschool levels are ordered but not
     * numbered -- Nursery is not "Class 0" -- so anything deriving a year from
     * the label (see {@code GradeLevel}) is meaningless here.
     */
    public boolean hasNumberedGrades() {
        return this != PRESCHOOL;
    }

    /** The levels a new school of this type starts with, in order. */
    public String[] defaultLevels() {
        return switch (this) {
            case PRESCHOOL -> new String[]{"Pre-Nursery", "Nursery", "LKG", "UKG"};
            case PRIMARY -> new String[]{"Class 1", "Class 2", "Class 3", "Class 4", "Class 5"};
            case SECONDARY -> new String[]{"Class 6", "Class 7", "Class 8", "Class 9", "Class 10"};
            case K10 -> new String[]{"Nursery", "LKG", "UKG", "Class 1", "Class 2", "Class 3",
                    "Class 4", "Class 5", "Class 6", "Class 7", "Class 8", "Class 9", "Class 10"};
        };
    }

    /**
     * What a new school of this type teaches, before anyone customises it.
     *
     * <p>A preschool's are areas of play and development, not academic
     * subjects -- which is the whole point of the distinction.
     */
    public String[] defaultActivities() {
        return switch (this) {
            case PRESCHOOL -> new String[]{"Circle Time", "Story Time", "Art & Craft",
                    "Music & Movement", "Outdoor Play", "Numbers & Shapes",
                    "Language & Rhymes", "Free Play"};
            default -> new String[]{"Mathematics", "Science", "English", "Social Studies",
                    "Hindi", "Computer Science", "Physical Education"};
        };
    }
}
