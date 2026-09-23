package com.concept.timetable.app;

import com.concept.assignment.data.SubjectAssignment;
import com.concept.assignment.data.SubjectAssignmentRepository;
import com.concept.common.AuditLogService;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.timetable.data.TimetableEntry;
import com.concept.timetable.data.TimetableRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for timetables — the teacher's read views (today/week),
 * the admin CRUD, and the dev-only pilot seed. Owns all decisions (day
 * validation, teacher-role checks, attendance-marked lookups, audit trail) so
 * the web controllers only bind and shape responses (ADR 0001).
 */
@Service
public class TimetableService {

    // Pilot constants — used by seed() only.
    private static final String PILOT_TEACHER_EMAIL = "teacher@greenwood.com";
    private static final UUID PILOT_SECTION_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID PILOT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID PILOT_ACADEMIC_YEAR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final Map<Integer, String[]> PERIOD_TIMES = new LinkedHashMap<>();
    static {
        PERIOD_TIMES.put(1, new String[]{"08:00", "08:45"});
        PERIOD_TIMES.put(2, new String[]{"08:45", "09:30"});
        PERIOD_TIMES.put(3, new String[]{"10:00", "10:45"});
        PERIOD_TIMES.put(4, new String[]{"10:45", "11:30"});
        PERIOD_TIMES.put(5, new String[]{"12:30", "13:15"});
    }

    private static final int[][] SEED_SLOTS = {
        {0, 1}, {0, 3}, {1, 2}, {1, 5}, {2, 1}, {2, 4}, {3, 3}, {3, 5}, {4, 2}, {4, 4}
    };

    private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI"};
    private static final Set<String> VALID_DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI");

    private final TimetableRepository timetableRepository;
    private final ClassSectionRepository classSectionRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final boolean devMode;

    public TimetableService(TimetableRepository timetableRepository,
                            ClassSectionRepository classSectionRepository,
                            AttendanceRepository attendanceRepository,
                            UserRepository userRepository,
                            AuditLogService auditLogService,
                            CurrentUserService currentUserService,
                            SubjectAssignmentRepository subjectAssignmentRepository,
                            @Value("${app.dev-mode:false}") boolean devMode) {
        this.timetableRepository = timetableRepository;
        this.classSectionRepository = classSectionRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.devMode = devMode;
    }

    // ─── Teacher read views ─────────────────────────────────────────────────

    public List<Map<String, Object>> todayTimetable(Authentication authentication) {
        String dayCode = todayDayCode();
        if (dayCode == null) {
            return List.of(); // Weekend
        }
        User teacher = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        LocalDate today = LocalDate.now();
        return timetableRepository
                .findByTeacherIdAndDayOfWeekOrderByPeriodNumber(teacher.getId(), dayCode)
                .stream()
                .map(entry -> buildPeriodResponse(entry, today))
                .collect(Collectors.toList());
    }

    public Map<String, List<Map<String, Object>>> weekTimetable(Authentication authentication) {
        User teacher = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (String day : DAY_CODES) {
            grouped.put(day, new ArrayList<>());
        }
        timetableRepository.findByTeacherId(teacher.getId()).forEach(entry -> {
            String day = entry.getDayOfWeek();
            if (grouped.containsKey(day)) {
                grouped.get(day).add(buildPeriodResponse(entry, null));
            }
        });
        grouped.values().forEach(list ->
                list.sort(Comparator.comparingInt(m -> (Integer) m.get("periodNumber"))));
        return grouped;
    }

    // ─── Dev-only pilot seed ────────────────────────────────────────────────

    public Map<String, Object> seedTimetable() {
        if (!devMode) {
            throw TimetableException.forbidden("Seed endpoints are disabled in production");
        }
        User teacher = userRepository.findByEmail(PILOT_TEACHER_EMAIL)
                .orElseThrow(() -> new IllegalStateException("Pilot teacher not found: " + PILOT_TEACHER_EMAIL));
        UUID teacherId = teacher.getId();

        List<TimetableEntry> existing = timetableRepository.findByTeacherId(teacherId);
        if (!existing.isEmpty()) {
            timetableRepository.deleteAll(existing);
        }

        ClassSection section = classSectionRepository.findByIdAndTenantId(PILOT_SECTION_ID, teacher.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Pilot section not found: " + PILOT_SECTION_ID));

        List<TimetableEntry> toSave = new ArrayList<>();
        for (int[] slot : SEED_SLOTS) {
            String[] times = PERIOD_TIMES.get(slot[1]);
            TimetableEntry entry = new TimetableEntry();
            entry.setId(UUID.randomUUID());
            entry.setTeacherId(teacherId);
            entry.setClassSection(section);
            entry.setDayOfWeek(DAY_CODES[slot[0]]);
            entry.setPeriodNumber(slot[1]);
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
        return resp;
    }

    // ─── Admin CRUD ─────────────────────────────────────────────────────────

    /**
     * Every period for one class section. The teacher reads resolve periods from
     * their own subject assignments, which is the wrong shape for a student --
     * a student wants their whole class's day, not one teacher's slice of it.
     * The caller supplies the section from the student's own record, so this is
     * already tenant-scoped by construction.
     */
    public List<Map<String, Object>> sectionTimetable(UUID classSectionId) {
        if (classSectionId == null) return List.of();
        return timetableRepository.findByClassSectionId(classSectionId).stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> adminList(UUID classSectionId, Authentication authentication) {
        List<TimetableEntry> entries;
        if (classSectionId != null) {
            entries = timetableRepository.findByClassSectionId(classSectionId);
        } else {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            entries = tenantId != null ? timetableRepository.findByTenantId(tenantId) : List.of();
        }
        return entries.stream().map(this::toMap).collect(Collectors.toList());
    }


    // ─── Slot validation ────────────────────────────────────────────────────
    //
    // A timetable had no server-side rules at all: the same teacher could be
    // put in two rooms at once, a class could be given two subjects in the
    // same period, and a period could run from 10:00 to 09:00. None of that is
    // a preference -- each one describes something that cannot happen -- so it
    // is refused here rather than in the form, which an HTTP client ignores.

    /**
     * A clash the admin should know about but which is not, by itself,
     * impossible. Returned alongside the saved entry rather than thrown.
     */
    public record SlotWarning(String message) {}

    private LocalTime parseTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw TimetableException.badRequest(field + " is required, as HH:mm.");
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw TimetableException.badRequest(field + " must look like 09:30, got \"" + raw + "\".");
        }
    }

    /** Half-open overlap: touching at an edge (08:45-09:30 after 08:00-08:45) is fine. */
    private boolean overlaps(LocalTime aStart, LocalTime aEnd, String bStartRaw, String bEndRaw) {
        LocalTime bStart;
        LocalTime bEnd;
        try {
            bStart = LocalTime.parse(bStartRaw.trim());
            bEnd = LocalTime.parse(bEndRaw.trim());
        } catch (RuntimeException e) {
            // A pre-existing row with unparseable times cannot be compared, and
            // must not block a new entry that is itself valid.
            return false;
        }
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private String sectionLabel(ClassSection section) {
        return (section.getGradeName() + " " + section.getSectionName()).trim();
    }

    /**
     * Checks a slot against everything already on the timetable.
     *
     * @param entryId the row being edited, excluded from the clash search so
     *                that saving an entry unchanged does not clash with itself
     * @return warnings worth showing, never null
     */
    private List<SlotWarning> validateSlot(UUID entryId, ClassSection section, User teacher, String day,
                                           int periodNumber, String startRaw, String endRaw,
                                           String subjectName) {
        LocalTime start = parseTime(startRaw, "Start time");
        LocalTime end = parseTime(endRaw, "End time");
        if (!end.isAfter(start)) {
            throw TimetableException.badRequest(
                    "A period has to end after it starts — " + startRaw + " to " + endRaw + " does not.");
        }

        // The same teacher cannot be in two places at once. Checked across every
        // section, which is the whole point: the clash the admin cannot see is
        // the one in the class they are not looking at.
        for (TimetableEntry other : timetableRepository
                .findByTeacherIdAndDayOfWeekOrderByPeriodNumber(teacher.getId(), day)) {
            if (other.getId().equals(entryId)) continue;
            if (overlaps(start, end, other.getStartTime(), other.getEndTime())) {
                String where = other.getClassSection() != null
                        ? sectionLabel(other.getClassSection()) : "another class";
                throw TimetableException.badRequest(teacher.getFullName() + " is already teaching "
                        + where + " on " + day + " from " + other.getStartTime() + " to " + other.getEndTime() + ".");
            }
        }

        // And a class cannot be taught two things at once. Same period number
        // counts as a clash even if the times were typed differently, because
        // the period is what the rest of the school day is organised around.
        for (TimetableEntry other : timetableRepository.findByClassSectionId(section.getId())) {
            if (other.getId().equals(entryId)) continue;
            if (!day.equals(other.getDayOfWeek())) continue;

            if (other.getPeriodNumber() == periodNumber) {
                throw TimetableException.badRequest(sectionLabel(section) + " already has "
                        + other.getSubjectName() + " in period " + periodNumber + " on " + day + ".");
            }
            if (overlaps(start, end, other.getStartTime(), other.getEndTime())) {
                throw TimetableException.badRequest(sectionLabel(section) + " already has "
                        + other.getSubjectName() + " on " + day + " from " + other.getStartTime()
                        + " to " + other.getEndTime() + ".");
            }
        }

        return teachesThere(teacher, section, subjectName);
    }

    /**
     * Whether this teacher is assigned to this section.
     *
     * <p>Deliberately a warning rather than a refusal. Schools really do put a
     * colleague in front of a class for a term, and an admin building their
     * first timetable has usually not filled in Teacher Assignments yet —
     * refusing here would block the ordinary path exactly the way the fee
     * approval gate did.
     */
    private List<SlotWarning> teachesThere(User teacher, ClassSection section, String subjectName) {
        // Keyed on the teacher's assignments, not the section's. Keying it on
        // the section meant a section with nothing configured said nothing at
        // all -- so a teacher assigned only to 6-B Science could be put on 7-A
        // English in silence, which is exactly the case this was meant to
        // catch. What the section has configured says nothing about whether
        // this teacher belongs in it.
        List<SubjectAssignment> mine = subjectAssignmentRepository.findByTeacher(teacher);
        if (mine.isEmpty()) {
            // Nothing configured for this teacher anywhere. Silence is not a
            // signal, and an admin building a first timetable has usually not
            // filled in Teacher Assignments yet.
            return List.of();
        }

        List<SubjectAssignment> here = mine.stream()
                .filter(a -> a.getClassSection() != null
                        && a.getClassSection().getId().equals(section.getId()))
                .toList();

        if (here.isEmpty()) {
            String elsewhere = mine.stream()
                    .map(SubjectAssignment::getClassSection)
                    .filter(java.util.Objects::nonNull)
                    .map(this::sectionLabel)
                    .distinct()
                    .collect(Collectors.joining(", "));
            return List.of(new SlotWarning(teacher.getFullName() + " is not assigned to "
                    + sectionLabel(section) + " — only " + elsewhere
                    + ". The period was saved; fix it in Teacher Assignments if that is wrong."));
        }

        // Assigned to the class, but for a different subject. Worth saying:
        // "Science teacher down for English" is usually a mistyped row, and
        // occasionally a deliberate cover arrangement.
        String subject = subjectName == null ? "" : subjectName.trim();
        boolean teachesThisSubject = subject.isEmpty() || here.stream()
                .anyMatch(a -> subject.equalsIgnoreCase(
                        a.getSubjectName() == null ? "" : a.getSubjectName().trim()));
        if (!teachesThisSubject) {
            String subjects = here.stream()
                    .map(SubjectAssignment::getSubjectName)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            return List.of(new SlotWarning(teacher.getFullName() + " is assigned to "
                    + sectionLabel(section) + " for " + subjects + ", not " + subject
                    + ". The period was saved anyway."));
        }
        return List.of();
    }

    public Map<String, Object> adminCreate(TimetableEntryRequest request, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        ClassSection classSection = classSectionRepository.findByIdAndTenantId(request.getClassSectionId(), tenantId).orElse(null);
        if (classSection == null) {
            throw TimetableException.badRequest("Class section not found");
        }
        User teacher = validateTeacher(request.getTeacherId(), tenantId);
        if (teacher == null) {
            throw TimetableException.badRequest("Teacher not found or not a TEACHER");
        }
        if (!VALID_DAYS.contains(request.getDayOfWeek())) {
            throw TimetableException.badRequest("dayOfWeek must be one of " + VALID_DAYS);
        }
        List<SlotWarning> warnings = validateSlot(null, classSection, teacher, request.getDayOfWeek(),
                request.getPeriodNumber(), request.getStartTime(), request.getEndTime(),
                request.getSubjectName());

        TimetableEntry entry = new TimetableEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(classSection.getTenantId());
        entry.setAcademicYearId(classSection.getAcademicYearId());
        entry.setClassSection(classSection);
        entry.setTeacherId(teacher.getId());
        entry.setDayOfWeek(request.getDayOfWeek());
        entry.setPeriodNumber(request.getPeriodNumber());
        entry.setStartTime(request.getStartTime());
        entry.setEndTime(request.getEndTime());
        entry.setSubjectName(request.getSubjectName());
        entry.setRoomNumber(request.getRoomNumber());
        timetableRepository.save(entry);

        auditLogService.log(authentication, "TIMETABLE_ENTRY_ADDED", "TimetableEntry", entry.getId(),
                "Added " + entry.getDayOfWeek() + " period " + entry.getPeriodNumber() + " (" + entry.getSubjectName()
                        + ") for " + classSection.getGradeName() + " - " + classSection.getSectionName());
        return withWarnings(toMap(entry), warnings);
    }

    public Map<String, Object> adminUpdate(UUID id, TimetableEntryRequest request, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TimetableEntry entry = timetableRepository.findByIdAndTenantId(id, tenantId).orElse(null);
        if (entry == null) {
            throw TimetableException.badRequest("Timetable entry not found");
        }
        if (request.getClassSectionId() != null) {
            ClassSection classSection = classSectionRepository.findByIdAndTenantId(request.getClassSectionId(), tenantId).orElse(null);
            if (classSection == null) {
                throw TimetableException.badRequest("Class section not found");
            }
            entry.setClassSection(classSection);
        }
        if (request.getTeacherId() != null) {
            User teacher = validateTeacher(request.getTeacherId(), tenantId);
            if (teacher == null) {
                throw TimetableException.badRequest("Teacher not found or not a TEACHER");
            }
            entry.setTeacherId(teacher.getId());
        }
        if (request.getDayOfWeek() != null) {
            if (!VALID_DAYS.contains(request.getDayOfWeek())) {
                throw TimetableException.badRequest("dayOfWeek must be one of " + VALID_DAYS);
            }
            entry.setDayOfWeek(request.getDayOfWeek());
        }
        if (request.getPeriodNumber() != null) entry.setPeriodNumber(request.getPeriodNumber());
        if (request.getStartTime() != null) entry.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) entry.setEndTime(request.getEndTime());
        if (request.getSubjectName() != null) entry.setSubjectName(request.getSubjectName());
        if (request.getRoomNumber() != null) entry.setRoomNumber(request.getRoomNumber());

        // Validated after the merge rather than from the request: every field
        // on an update is optional, so only the resulting row says what this
        // period will actually be. The entry's own id is excluded, or moving a
        // period by five minutes would clash with where it already is.
        User onDuty = userRepository.findByIdAndTenantId(entry.getTeacherId(), tenantId).orElse(null);
        List<SlotWarning> warnings = onDuty == null ? List.of()
                : validateSlot(entry.getId(), entry.getClassSection(), onDuty, entry.getDayOfWeek(),
                        entry.getPeriodNumber(), entry.getStartTime(), entry.getEndTime(),
                        entry.getSubjectName());

        timetableRepository.save(entry);
        auditLogService.log(authentication, "TIMETABLE_ENTRY_UPDATED", "TimetableEntry", entry.getId(),
                "Updated " + entry.getDayOfWeek() + " period " + entry.getPeriodNumber() + " (" + entry.getSubjectName() + ")");
        return withWarnings(toMap(entry), warnings);
    }

    private Map<String, Object> withWarnings(Map<String, Object> body, List<SlotWarning> warnings) {
        if (warnings.isEmpty()) {
            return body;
        }
        Map<String, Object> withWarnings = new LinkedHashMap<>(body);
        withWarnings.put("warnings", warnings.stream().map(SlotWarning::message).collect(Collectors.toList()));
        return withWarnings;
    }

    public Map<String, Object> adminDelete(UUID id, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        TimetableEntry entry = timetableRepository.findByIdAndTenantId(id, tenantId).orElse(null);
        if (entry == null) {
            throw TimetableException.badRequest("Timetable entry not found");
        }
        String summary = entry.getDayOfWeek() + " period " + entry.getPeriodNumber() + " (" + entry.getSubjectName() + ")";
        timetableRepository.delete(entry);
        auditLogService.log(authentication, "TIMETABLE_ENTRY_REMOVED", "TimetableEntry", id, "Removed " + summary);
        return Map.of("status", "deleted");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private User validateTeacher(UUID teacherId, UUID tenantId) {
        if (teacherId == null) return null;
        User teacher = userRepository.findByIdAndTenantId(teacherId, tenantId).orElse(null);
        if (teacher == null || teacher.getRole() != UserRole.TEACHER) return null;
        return teacher;
    }

    private String todayDayCode() {
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        return switch (dow) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            default -> null;
        };
    }

    private Map<String, Object> buildPeriodResponse(TimetableEntry entry, LocalDate checkDate) {
        ClassSection section = entry.getClassSection();
        boolean attendanceMarked = false;
        if (checkDate != null && section != null) {
            List<Attendance> records = attendanceRepository.findByClassSectionAndAttendanceDate(section, checkDate);
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
                ? section.getGradeName() + " – " + section.getSectionName() : "");
        map.put("classSectionId", section != null ? section.getId() : null);
        map.put("attendanceMarked", attendanceMarked);
        return map;
    }

    private Map<String, Object> toMap(TimetableEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("classSectionId", entry.getClassSection() != null ? entry.getClassSection().getId() : null);
        map.put("className", entry.getClassSection() != null
                ? entry.getClassSection().getGradeName() + " – " + entry.getClassSection().getSectionName() : "");
        map.put("teacherId", entry.getTeacherId());
        map.put("dayOfWeek", entry.getDayOfWeek());
        map.put("periodNumber", entry.getPeriodNumber());
        map.put("startTime", entry.getStartTime());
        map.put("endTime", entry.getEndTime());
        map.put("subjectName", entry.getSubjectName());
        map.put("roomNumber", entry.getRoomNumber());
        return map;
    }
}
