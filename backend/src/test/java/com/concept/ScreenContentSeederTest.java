package com.concept;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The demo database has to have something in it.
 *
 * Every seeder before this one built structure -- sections, students, accounts --
 * and the screens that read timetables, attendance, announcements and marks came
 * up empty against a database everyone described as seeded. That hid two bugs at
 * once: the app filtered timetable rows on "MONDAY" while the column is
 * varchar(3) and holds "MON", and a contrast check passed an attendance calendar
 * that had drawn no cells at all.
 *
 * So these are not tests of the seeder's cleverness. They are the assertion that
 * the tables behind each screen are not empty, and that the few values the app
 * matches on literally are the values it expects.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
public class ScreenContentSeederTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ScreenContentSeeder seeder;

    private int countIn(String table) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, TENANT_ID);
        return n == null ? 0 : n;
    }

    /**
     * The pilot teacher teaches one subject, in one room.
     *
     * The first version of the seeder gave every section's every period to
     * teacher@greenwood.com, so a Maths teacher's own week contained Science in
     * a Science Lab and PE on a Sports Ground. Two Playwright specs caught it,
     * but only after a full backend boot and a browser; this catches the same
     * thing in the unit suite. The counts matter as much as the values -- an
     * empty week would satisfy a bare "every row is Mathematics" loop.
     */
    @Test
    void pilotTeacherOwnsOnlyMathematicsInTheirOwnRoom() {
        UUID pilot = jdbc.queryForObject(
            "SELECT id FROM users WHERE email = ?", UUID.class, "teacher@greenwood.com");
        assertNotNull(pilot, "no pilot teacher, so this asserts nothing");

        List<String> subjects = jdbc.queryForList(
            "SELECT DISTINCT subject_name FROM timetable_entries WHERE teacher_id = ?",
            String.class, pilot);
        List<String> rooms = jdbc.queryForList(
            "SELECT DISTINCT room_number FROM timetable_entries WHERE teacher_id = ?",
            String.class, pilot);
        Integer periods = jdbc.queryForObject(
            "SELECT COUNT(*) FROM timetable_entries WHERE teacher_id = ?", Integer.class, pilot);

        assertTrue(periods != null && periods > 0,
            "the pilot teacher has no periods at all, so the timetable screen is blank");
        assertEquals(List.of("Mathematics"), subjects,
            "the pilot teacher is teaching subjects that are not theirs: " + subjects);
        assertEquals(List.of("Room 204"), rooms,
            "the pilot teacher is teaching outside their own room: " + rooms);
    }

    /**
     * Every other section still gets a full timetable -- the fix above must not
     * be a fix by deletion. Students read their timetable by section, not by
     * teacher, so a section with no periods is a blank screen for a whole class.
     */
    @Test
    void everySectionStillHasAWeekOfPeriods() {
        List<UUID> sections = jdbc.queryForList(
            "SELECT DISTINCT class_section_id FROM timetable_entries WHERE tenant_id = ?",
            UUID.class, TENANT_ID);
        assertEquals(5, sections.size(),
            "expected all five demo sections to have a timetable, got " + sections.size());
        for (UUID section : sections) {
            Integer days = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT day_of_week) FROM timetable_entries WHERE class_section_id = ?",
                Integer.class, section);
            assertEquals(5, days, "section " + section + " does not have a full school week");
        }
    }

    @Test
    void everyScreenBackingTableHasRows() {
        for (String table : List.of(
                "timetable_entries", "attendance", "announcements", "assessments", "teacher_tasks")) {
            assertTrue(countIn(table) > 0,
                table + " is empty, so the screen that reads it renders nothing -- "
                + "which is how the MON/MONDAY mismatch went unnoticed.");
        }
        Integer scores = jdbc.queryForObject(
            "SELECT COUNT(*) FROM student_assessment_scores", Integer.class);
        assertTrue(scores != null && scores > 0, "no marks, so the results screen is blank");
    }

    /**
     * The seeder has to survive a wipe, because /test/reset performs one on every
     * Playwright run: it clears attendance and teacher tasks and puts back only
     * Arjun's, hardcoded to June 2026. Seeding once at boot was therefore not
     * enough -- the screens went empty again after the first run and stayed empty
     * until the next restart, which is the exact failure this seeder exists to
     * prevent. /test/reset now calls seedAll(); this checks seedAll() can rebuild
     * what a wipe took, rather than skipping because it ran once already.
     */
    @Test
    void seedAllRestoresContentAfterAWipe() {
        int before = countIn("attendance");
        assertTrue(before > 0, "nothing to wipe; the boot seed did not run");

        jdbc.update("DELETE FROM attendance WHERE tenant_id = ?", TENANT_ID);
        assertEquals(0, countIn("attendance"), "the wipe did not take");

        seeder.seedAll();

        assertTrue(countIn("attendance") > 0,
            "seedAll() did not rebuild attendance after a wipe, so /test/reset "
            + "would leave the calendar empty until the backend restarted");
    }

    @Test
    void timetableDaysUseTheThreeLetterCodesTheColumnHolds() {
        List<String> days = jdbc.queryForList(
            "SELECT DISTINCT day_of_week FROM timetable_entries WHERE tenant_id = ?",
            String.class, TENANT_ID);

        assertFalse(days.isEmpty(), "no timetable rows to check");
        assertTrue(List.of("MON", "TUE", "WED", "THU", "FRI").containsAll(days),
            "day_of_week is varchar(3); anything longer silently matches nothing. Found: " + days);
    }

    @Test
    void attendanceCarriesMoreThanOneStatus() {
        // A register that is 100% PRESENT looks identical to an empty one on the
        // calendar, and would have hidden the same class of bug all over again.
        List<String> statuses = jdbc.queryForList(
            "SELECT DISTINCT status FROM attendance WHERE tenant_id = ?", String.class, TENANT_ID);

        assertTrue(statuses.size() > 1,
            "attendance needs absences to be worth rendering; found only " + statuses);
    }

    @Test
    void announcementsReachEveryDemoGrade() {
        // announcements are matched on grade_name or the literal "ALL", so a
        // target_grade that matches no section is invisible to every student.
        List<String> targets = jdbc.queryForList(
            "SELECT DISTINCT target_grade FROM announcements WHERE tenant_id = ?",
            String.class, TENANT_ID);
        assertTrue(targets.contains("ALL"), "nothing addressed to the whole school: " + targets);

        List<String> grades = jdbc.queryForList(
            "SELECT DISTINCT grade_name FROM class_sections WHERE tenant_id = ?",
            String.class, TENANT_ID);
        for (String t : targets) {
            assertTrue(t.equals("ALL") || grades.contains(t),
                "announcement targets \"" + t + "\", which matches no section: " + grades);
        }
    }
}
