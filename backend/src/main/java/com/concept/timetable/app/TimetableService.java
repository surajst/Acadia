package com.concept.timetable.app;

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
    private final boolean devMode;

    public TimetableService(TimetableRepository timetableRepository,
                            ClassSectionRepository classSectionRepository,
                            AttendanceRepository attendanceRepository,
                            UserRepository userRepository,
                            AuditLogService auditLogService,
                            CurrentUserService currentUserService,
                            @Value("${app.dev-mode:false}") boolean devMode) {
        this.timetableRepository = timetableRepository;
        this.classSectionRepository = classSectionRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
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
        return toMap(entry);
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

        timetableRepository.save(entry);
        auditLogService.log(authentication, "TIMETABLE_ENTRY_UPDATED", "TimetableEntry", entry.getId(),
                "Updated " + entry.getDayOfWeek() + " period " + entry.getPeriodNumber() + " (" + entry.getSubjectName() + ")");
        return toMap(entry);
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
