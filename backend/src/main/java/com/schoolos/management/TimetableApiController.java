package com.schoolos.management;

import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
public class TimetableApiController {

    // Pilot constants — used by /seed only ────────────────────────────────────
    private static final String PILOT_TEACHER_EMAIL = "teacher@greenwood.com";
    private static final UUID PILOT_SECTION_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PILOT_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID PILOT_ACADEMIC_YEAR_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Period time map: periodNumber -> [startTime, endTime]
    private static final Map<Integer, String[]> PERIOD_TIMES = new LinkedHashMap<>();
    static {
        PERIOD_TIMES.put(1, new String[]{"08:00", "08:45"});
        PERIOD_TIMES.put(2, new String[]{"08:45", "09:30"});
        PERIOD_TIMES.put(3, new String[]{"10:00", "10:45"});
        PERIOD_TIMES.put(4, new String[]{"10:45", "11:30"});
        PERIOD_TIMES.put(5, new String[]{"12:30", "13:15"});
    }

    // Seed slots: [dayOfWeek, periodNumber]
    private static final int[][] SEED_SLOTS = {
        // MON
        {0, 1}, {0, 3},
        // TUE
        {1, 2}, {1, 5},
        // WED
        {2, 1}, {2, 4},
        // THU
        {3, 3}, {3, 5},
        // FRI
        {4, 2}, {4, 4}
    };

    private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI"};

    @Autowired
    private TimetableRepository timetableRepository;

    @Value("${app.dev-mode:false}")
    private boolean devMode;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private UserRepository userRepository;

    // ─── GET /api/teacher/timetable/today ────────────────────────────────────

    @GetMapping("/timetable/today")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<Map<String, Object>>> getTodayTimetable(Authentication authentication) {
        try {
            String dayCode = todayDayCode();
            if (dayCode == null) {
                // Weekend — return empty list
                return ResponseEntity.ok(Collections.emptyList());
            }

            User teacher = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

            List<TimetableEntry> entries = timetableRepository
                    .findByTeacherIdAndDayOfWeekOrderByPeriodNumber(teacher.getId(), dayCode);

            LocalDate today = LocalDate.now();

            List<Map<String, Object>> result = entries.stream()
                    .map(entry -> buildPeriodResponse(entry, today))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // ─── GET /api/teacher/timetable/week ─────────────────────────────────────

    @GetMapping("/timetable/week")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getWeekTimetable(Authentication authentication) {
        try {
            User teacher = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

            List<TimetableEntry> allEntries = timetableRepository.findByTeacherId(teacher.getId());

            // Group by day, preserve MON→FRI order
            Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (String day : DAY_CODES) {
                grouped.put(day, new ArrayList<>());
            }

            allEntries.forEach(entry -> {
                String day = entry.getDayOfWeek();
                if (grouped.containsKey(day)) {
                    grouped.get(day).add(buildPeriodResponse(entry, null));
                }
            });

            // Sort each day's list by period number
            grouped.values().forEach(list ->
                    list.sort(Comparator.comparingInt(m -> (Integer) m.get("periodNumber")))
            );

            return ResponseEntity.ok(grouped);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // ─── POST /api/teacher/timetable/seed ────────────────────────────────────
    // DEV ONLY - gated behind app.dev-mode flag AND ADMIN role

    @PostMapping("/timetable/seed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> seedTimetable() {
        if (!devMode) {
            return ResponseEntity.status(403).body(Map.of("error", "Seed endpoints are disabled in production"));
        }
        try {
            // Resolve the actual teacher from the DB (avoids hardcoded UUID mismatch)
            com.schoolos.user.User teacher = userRepository.findByEmail(PILOT_TEACHER_EMAIL)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pilot teacher not found: " + PILOT_TEACHER_EMAIL));
            UUID teacherId = teacher.getId();

            // Clear any existing entries before seeding to prevent duplicates
            List<TimetableEntry> existing = timetableRepository.findByTeacherId(teacherId);
            if (!existing.isEmpty()) {
                timetableRepository.deleteAll(existing);
            }

            ClassSection section = classSectionRepository.findById(PILOT_SECTION_ID)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pilot section not found: " + PILOT_SECTION_ID));

            List<TimetableEntry> toSave = new ArrayList<>();
            for (int[] slot : SEED_SLOTS) {
                int dayIndex = slot[0];
                int periodNum = slot[1];
                String[] times = PERIOD_TIMES.get(periodNum);

                TimetableEntry entry = new TimetableEntry();
                entry.setId(UUID.randomUUID());
                entry.setTeacherId(teacherId);
                entry.setClassSection(section);
                entry.setDayOfWeek(DAY_CODES[dayIndex]);
                entry.setPeriodNumber(periodNum);
                entry.setStartTime(times[0]);
                entry.setEndTime(times[1]);
                entry.setSubjectName("Mathematics");
                entry.setRoomNumber("Room 204");
                entry.setTenantId(PILOT_TENANT_ID);
                entry.setAcademicYearId(PILOT_ACADEMIC_YEAR_ID);

                toSave.add(entry);
            }

            timetableRepository.saveAll(toSave);

            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "seeded");
            resp.put("count", toSave.size());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns today's day code (MON/TUE/WED/THU/FRI) or null on weekends.
     */
    private String todayDayCode() {
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        return switch (dow) {
            case MONDAY    -> "MON";
            case TUESDAY   -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY  -> "THU";
            case FRIDAY    -> "FRI";
            default        -> null; // SAT / SUN
        };
    }

    /**
     * Builds the response map for a single timetable period.
     * @param entry    the timetable entry
     * @param checkDate the date to check attendance against; null skips the check (returns false)
     */
    private Map<String, Object> buildPeriodResponse(TimetableEntry entry, LocalDate checkDate) {
        ClassSection section = entry.getClassSection();

        boolean attendanceMarked = false;
        if (checkDate != null && section != null) {
            List<Attendance> records = attendanceRepository
                    .findByClassSectionAndAttendanceDate(section, checkDate);
            attendanceMarked = !records.isEmpty();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("periodNumber", entry.getPeriodNumber());
        map.put("startTime", entry.getStartTime());
        map.put("endTime", entry.getEndTime());
        map.put("subjectName", entry.getSubjectName());
        map.put("roomNumber", entry.getRoomNumber());
        map.put("className", section != null
                ? section.getGradeName() + " – " + section.getSectionName()
                : "");
        map.put("classSectionId", section != null ? section.getId() : null);
        map.put("attendanceMarked", attendanceMarked);
        return map;
    }
}
