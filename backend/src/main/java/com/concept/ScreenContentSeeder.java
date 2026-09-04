package com.concept;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Fills the tables the app's screens actually read from.
 *
 * The other seeders build the skeleton -- tenants, sections, 500 students, user
 * accounts -- but nothing wrote a timetable entry, an attendance record, an
 * announcement or a mark. So every one of those screens rendered empty against
 * a "fully seeded" database, and an empty screen hides its own bugs: the app
 * filtered timetables on "MONDAY" while this column is varchar(3) and holds
 * "MON", and nobody noticed because there was never a row to mismatch. A
 * contrast check of mine also passed an attendance calendar that had drawn
 * zero cells. An empty screen is not a clean screen.
 *
 * Everything here hangs off the accounts people actually log in as, and every
 * value is derived deterministically from the row's own identity, so a re-run
 * produces the same database rather than a second copy of it.
 */
@Component
@Order(5)
@ConditionalOnProperty(name = "app.dev-mode", havingValue = "true")
public class ScreenContentSeeder implements CommandLineRunner {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID ACADEMIC_YEAR_ID = UUID.fromString("00000000-0000-0000-0000-111111111111");

    /** The five demo sections, Grade 6 A through Grade 10 A, as AcademicDataSeeder names them. */
    private static final UUID[] SECTION_IDS = {
        UUID.fromString("66666666-6666-6666-6666-666666666666"),
        UUID.fromString("77777777-7777-7777-7777-777777777777"),
        UUID.fromString("88888888-8888-8888-8888-888888888888"),
        UUID.fromString("99999999-9999-9999-9999-999999999999"),
        UUID.fromString("10101010-1010-1010-1010-101010101010"),
    };

    /** Three letters, because timetable_entries.day_of_week is varchar(3). */
    private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI"};

    private static final String[][] PERIODS = {
        {"08:30", "09:15"}, {"09:20", "10:05"}, {"10:20", "11:05"},
        {"11:10", "11:55"}, {"12:40", "13:25"}, {"13:30", "14:15"},
    };

    private static final String[] SUBJECT_NAMES = {
        "Mathematics", "Science", "English", "Social Science", "Hindi", "Physical Education",
    };

    /** The four subject_type values the curriculum seeder uses, for marks. */
    private static final String[] SUBJECT_TYPES = {"MATH", "SCIENCE", "ENGLISH", "SOCIAL_SCIENCE"};

    /** Attendance is only worth seeding for the students someone will actually open. */
    private static final int STUDENTS_PER_SECTION = 25;
    private static final int SCHOOL_DAYS = 20;

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(String... args) {
        UUID teacherId = pilotTeacherId();
        if (teacherId == null) {
            System.out.println(">> Screen Content Seeder -> no teacher@greenwood.com yet, skipping.");
            return;
        }

        int timetable = seedTimetable(teacherId);
        int attendance = seedAttendance();
        int announcements = seedAnnouncements(teacherId);
        int marks = seedAssessmentsAndScores(teacherId);
        int tasks = seedTeacherTasks(teacherId);

        System.out.printf(">> Screen Content Seeder -> %d timetable, %d attendance, %d announcements, "
            + "%d marks, %d tasks.%n", timetable, attendance, announcements, marks, tasks);
    }

    /**
     * The pilot teacher account, looked up rather than hardcoded: ScaleTestDataSeeder
     * gives it a random id. Content owned by an id nobody logs in as would leave the
     * screens just as empty as before.
     */
    private UUID pilotTeacherId() {
        List<UUID> ids = jdbc.query(
            "SELECT id FROM users WHERE email = ?",
            (rs, n) -> UUID.fromString(rs.getString("id")),
            "teacher@greenwood.com");
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<UUID> studentsIn(UUID sectionId, int limit) {
        return jdbc.query(
            "SELECT id FROM students WHERE class_section_id = ? AND tenant_id = ? ORDER BY roll_number",
            (rs, n) -> UUID.fromString(rs.getString("id")),
            sectionId, TENANT_ID)
            .stream().limit(limit).toList();
    }

    private String gradeNameOf(UUID sectionId) {
        List<String> names = jdbc.query(
            "SELECT grade_name FROM class_sections WHERE id = ?",
            (rs, n) -> rs.getString("grade_name"), sectionId);
        return names.isEmpty() ? null : names.get(0);
    }

    private String roomOf(UUID sectionId) {
        List<String> rooms = jdbc.query(
            "SELECT room_number FROM class_sections WHERE id = ?",
            (rs, n) -> rs.getString("room_number"), sectionId);
        return rooms.isEmpty() || rooms.get(0) == null ? "Room 204" : rooms.get(0);
    }

    /**
     * Whether THIS seeder's rows are already in place -- not whether the table has
     * anything in it. DemoTestHarness writes its own announcement and timetable
     * rows as part of a smoke run, so a table-level "is it empty" check made this
     * seeder skip every block and quietly do nothing. Each block writes a fixed
     * set of deterministic ids in one batch, so the first id is a reliable marker
     * for the whole batch.
     */
    private boolean alreadySeeded(String table, UUID markerId) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, markerId);
        return n != null && n > 0;
    }

    /** A stable id for a row, so re-running the seeder cannot double it. */
    private static UUID idFor(String... parts) {
        return UUID.nameUUIDFromBytes(String.join("|", parts).getBytes());
    }

    /** Deterministic spread in [0, bound), so the demo database is the same every time. */
    private static int spread(int bound, String... parts) {
        return Math.floorMod(String.join("|", parts).hashCode(), bound);
    }

    /* --------------------------------------------------------------- timetable --- */

    private int seedTimetable(UUID teacherId) {
        List<Object[]> rows = new ArrayList<>();
        for (UUID sectionId : SECTION_IDS) {
            if (gradeNameOf(sectionId) == null) continue;
            String room = roomOf(sectionId);
            for (int d = 0; d < DAY_CODES.length; d++) {
                for (int p = 0; p < PERIODS.length; p++) {
                    // Rotate the subject by day as well as period, so no two days look alike.
                    String subject = SUBJECT_NAMES[(p + d) % SUBJECT_NAMES.length];
                    // Science and PE happen somewhere other than the home room.
                    String where = subject.equals("Science") ? "Science Lab"
                        : subject.equals("Physical Education") ? "Sports Ground"
                        : room;
                    rows.add(new Object[]{
                        idFor("timetable", sectionId.toString(), DAY_CODES[d], String.valueOf(p)),
                        TENANT_ID, ACADEMIC_YEAR_ID, sectionId, teacherId,
                        DAY_CODES[d], p + 1, PERIODS[p][0], PERIODS[p][1], subject, where,
                    });
                }
            }
        }
        if (rows.isEmpty() || alreadySeeded("timetable_entries", (UUID) rows.get(0)[0])) return 0;
        jdbc.batchUpdate(
            "INSERT INTO timetable_entries (id, tenant_id, academic_year_id, class_section_id, "
            + "teacher_id, day_of_week, period_number, start_time, end_time, subject_name, room_number) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
        return rows.size();
    }

    /* -------------------------------------------------------------- attendance --- */

    private int seedAttendance() {
        List<LocalDate> schoolDays = new ArrayList<>();
        LocalDate day = LocalDate.now();
        while (schoolDays.size() < SCHOOL_DAYS) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                schoolDays.add(day);
            }
            day = day.minusDays(1);
        }

        List<Object[]> rows = new ArrayList<>();
        for (UUID sectionId : SECTION_IDS) {
            for (UUID studentId : studentsIn(sectionId, STUDENTS_PER_SECTION)) {
                for (LocalDate d : schoolDays) {
                    // Roughly 88% present. Enough absences that the calendar has
                    // something to colour, few enough that the percentages stay plausible.
                    int roll = spread(100, studentId.toString(), d.toString());
                    String status = roll < 88 ? "PRESENT"
                        : roll < 95 ? "ABSENT"
                        : roll < 99 ? "TARDY"
                        : "EXCUSED";
                    rows.add(new Object[]{
                        idFor("attendance", studentId.toString(), d.toString()),
                        TENANT_ID, ACADEMIC_YEAR_ID, sectionId, studentId,
                        java.sql.Date.valueOf(d), status,
                        status.equals("EXCUSED") ? "Medical leave" : null,
                    });
                }
            }
        }
        if (rows.isEmpty() || alreadySeeded("attendance", (UUID) rows.get(0)[0])) return 0;
        jdbc.batchUpdate(
            "INSERT INTO attendance (id, tenant_id, academic_year_id, class_section_id, student_id, "
            + "attendance_date, status, remarks) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows);
        return rows.size();
    }

    /* ----------------------------------------------------------- announcements --- */

    private int seedAnnouncements(UUID teacherId) {
        // target_grade matches class_sections.grade_name, or "ALL" for the whole school.
        String[][] items = {
            {"ALL", "Annual Sports Day on the 18th",
             "Track and field events run from 8am on the main ground. Students should come in "
             + "house colours. Parents are welcome from 9am; seating is behind the north fence."},
            {"ALL", "Library open through the half-term break",
             "The library will stay open 9am-1pm on weekdays during the break. Borrowed books are "
             + "due back the Monday we return."},
            {"ALL", "Photograph day moved to Thursday",
             "Class photographs have moved from Tuesday to Thursday because of the assembly "
             + "rehearsal. Full uniform, please."},
            {"Grade 6", "Grade 6 science fair entries close Friday",
             "Bring your project outline to Ms Rao before Friday lunchtime. Working models score "
             + "higher than posters, but either is welcome."},
            {"Grade 8", "Grade 8 field trip consent forms",
             "Consent forms for the museum trip need a parent signature by the end of the week. "
             + "Spare copies are in the front office."},
            {"Grade 10", "Grade 10 board exam revision timetable",
             "Extra revision sessions run after school on Tuesdays and Thursdays from this week "
             + "until the pre-boards. No sign-up needed."},
        };

        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            rows.add(new Object[]{
                idFor("announcement", items[i][0], items[i][1]),
                TENANT_ID, ACADEMIC_YEAR_ID, teacherId,
                items[i][0], items[i][1], items[i][2],
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(i * 2L + 1)),
            });
        }
        if (alreadySeeded("announcements", (UUID) rows.get(0)[0])) return 0;
        jdbc.batchUpdate(
            "INSERT INTO announcements (id, tenant_id, academic_year_id, created_by, target_grade, "
            + "title, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows);
        return rows.size();
    }

    /* ------------------------------------------------------------------ marks --- */

    private int seedAssessmentsAndScores(UUID teacherId) {
        List<Object[]> assessments = new ArrayList<>();
        List<Object[]> scores = new ArrayList<>();

        for (UUID sectionId : SECTION_IDS) {
            if (gradeNameOf(sectionId) == null) continue;
            List<UUID> students = studentsIn(sectionId, STUDENTS_PER_SECTION);

            for (String subject : SUBJECT_TYPES) {
                UUID assessmentId = idFor("assessment", sectionId.toString(), subject);
                LocalDate on = LocalDate.now().minusDays(7L + spread(21, sectionId.toString(), subject));
                assessments.add(new Object[]{
                    assessmentId, TENANT_ID, ACADEMIC_YEAR_ID, sectionId, teacherId,
                    titleFor(subject), subject, "TERM1", 100, java.sql.Date.valueOf(on),
                });

                for (UUID studentId : students) {
                    // 55-95, clustered by student so one child reads as consistently strong
                    // or consistently struggling across subjects rather than as noise.
                    int base = 55 + spread(35, studentId.toString());
                    int score = Math.min(100, base + spread(11, studentId.toString(), subject) - 5);
                    scores.add(new Object[]{
                        idFor("score", assessmentId.toString(), studentId.toString()),
                        assessmentId, studentId, teacherId, score,
                        java.sql.Timestamp.valueOf(on.plusDays(2).atTime(16, 0)),
                    });
                }
            }
        }

        if (assessments.isEmpty() || alreadySeeded("assessments", (UUID) assessments.get(0)[0])) return 0;
        jdbc.batchUpdate(
            "INSERT INTO assessments (id, tenant_id, academic_year_id, class_section_id, "
            + "created_by_teacher_id, title, subject_type, term, max_score, assessment_date) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", assessments);
        jdbc.batchUpdate(
            "INSERT INTO student_assessment_scores (id, assessment_id, student_id, "
            + "graded_by_teacher_id, score, graded_at) VALUES (?, ?, ?, ?, ?, ?)", scores);
        return assessments.size() + scores.size();
    }

    private static String titleFor(String subjectType) {
        return switch (subjectType) {
            case "MATH" -> "Unit Test 1 — Fractions and Decimals";
            case "SCIENCE" -> "Unit Test 1 — Matter and Materials";
            case "ENGLISH" -> "Unit Test 1 — Comprehension and Grammar";
            default -> "Unit Test 1 — Maps and Regions";
        };
    }

    /* ------------------------------------------------------------------ tasks --- */

    private int seedTeacherTasks(UUID teacherId) {
        String[][] items = {
            {"HOMEWORK", "MATH", "Fractions worksheet, questions 1-12",
             "Show your working for each one. We will go through the tricky ones on Thursday."},
            {"READING", "ENGLISH", "Read chapter 4 of the class novel",
             "Note down two questions you would ask the narrator. We will use them in the discussion."},
            {"PROJECT", "SCIENCE", "Build a working circuit diagram",
             "A drawing is fine if you cannot get the parts. Label the switch, the cell and the bulb."},
            {"PRACTICE", "SOCIAL_SCIENCE", "Label the rivers on the blank map",
             "Use the atlas on page 42. Colour the tributaries a lighter shade than the main river."},
        };

        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < SECTION_IDS.length; i++) {
            String gradeName = gradeNameOf(SECTION_IDS[i]);
            if (gradeName == null) continue;
            int standard = 6 + i;
            for (int j = 0; j < items.length; j++) {
                rows.add(new Object[]{
                    idFor("task", SECTION_IDS[i].toString(), items[j][0], items[j][1]),
                    TENANT_ID, ACADEMIC_YEAR_ID, teacherId,
                    items[j][2], items[j][3], items[j][0], items[j][1],
                    standard, 20 + j * 10, true, "ACTIVE",
                    java.sql.Date.valueOf(LocalDate.now().plusDays(2L + j)),
                    java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(j + 1L)),
                });
            }
        }
        if (rows.isEmpty() || alreadySeeded("teacher_tasks", (UUID) rows.get(0)[0])) return 0;
        jdbc.batchUpdate(
            "INSERT INTO teacher_tasks (id, tenant_id, academic_year_id, created_by_teacher_id, "
            + "title, description, task_type, subject_type, standard, xp_reward, assigned_to_class, "
            + "task_status, due_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
        return rows.size();
    }
}
