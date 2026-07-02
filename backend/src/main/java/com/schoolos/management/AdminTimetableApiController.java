package com.schoolos.management;

import com.schoolos.common.AuditLogService;
import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/timetable")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTimetableApiController {

    private static final Set<String> VALID_DAYS = Set.of("MON", "TUE", "WED", "THU", "FRI");

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) UUID classSectionId, Authentication authentication) {
        List<TimetableEntry> entries;
        if (classSectionId != null) {
            entries = timetableRepository.findByClassSectionId(classSectionId);
        } else {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            entries = tenantId != null ? timetableRepository.findByTenantId(tenantId) : List.of();
        }
        return ResponseEntity.ok(entries.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TimetableEntryRequest request, Authentication authentication) {
        ClassSection classSection = classSectionRepository.findById(request.getClassSectionId()).orElse(null);
        if (classSection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Class section not found"));
        }
        User teacher = validateTeacher(request.getTeacherId());
        if (teacher == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found or not a TEACHER"));
        }
        if (!VALID_DAYS.contains(request.getDayOfWeek())) {
            return ResponseEntity.badRequest().body(Map.of("error", "dayOfWeek must be one of " + VALID_DAYS));
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

        return ResponseEntity.ok(toMap(entry));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody TimetableEntryRequest request, Authentication authentication) {
        TimetableEntry entry = timetableRepository.findById(id).orElse(null);
        if (entry == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Timetable entry not found"));
        }
        if (request.getClassSectionId() != null) {
            ClassSection classSection = classSectionRepository.findById(request.getClassSectionId()).orElse(null);
            if (classSection == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Class section not found"));
            }
            entry.setClassSection(classSection);
        }
        if (request.getTeacherId() != null) {
            User teacher = validateTeacher(request.getTeacherId());
            if (teacher == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Teacher not found or not a TEACHER"));
            }
            entry.setTeacherId(teacher.getId());
        }
        if (request.getDayOfWeek() != null) {
            if (!VALID_DAYS.contains(request.getDayOfWeek())) {
                return ResponseEntity.badRequest().body(Map.of("error", "dayOfWeek must be one of " + VALID_DAYS));
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

        return ResponseEntity.ok(toMap(entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id, Authentication authentication) {
        TimetableEntry entry = timetableRepository.findById(id).orElse(null);
        if (entry == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Timetable entry not found"));
        }
        String summary = entry.getDayOfWeek() + " period " + entry.getPeriodNumber() + " (" + entry.getSubjectName() + ")";
        timetableRepository.delete(entry);
        auditLogService.log(authentication, "TIMETABLE_ENTRY_REMOVED", "TimetableEntry", id, "Removed " + summary);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    private User validateTeacher(UUID teacherId) {
        if (teacherId == null) return null;
        User teacher = userRepository.findById(teacherId).orElse(null);
        if (teacher == null || teacher.getRole() != UserRole.TEACHER) return null;
        return teacher;
    }

    private Map<String, Object> toMap(TimetableEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("classSectionId", entry.getClassSection() != null ? entry.getClassSection().getId() : null);
        map.put("className", entry.getClassSection() != null
                ? entry.getClassSection().getGradeName() + " – " + entry.getClassSection().getSectionName()
                : "");
        map.put("teacherId", entry.getTeacherId());
        map.put("dayOfWeek", entry.getDayOfWeek());
        map.put("periodNumber", entry.getPeriodNumber());
        map.put("startTime", entry.getStartTime());
        map.put("endTime", entry.getEndTime());
        map.put("subjectName", entry.getSubjectName());
        map.put("roomNumber", entry.getRoomNumber());
        return map;
    }

    public static class TimetableEntryRequest {
        private UUID classSectionId;
        private UUID teacherId;
        private String dayOfWeek;
        private Integer periodNumber;
        private String startTime;
        private String endTime;
        private String subjectName;
        private String roomNumber;

        public UUID getClassSectionId() { return classSectionId; }
        public void setClassSectionId(UUID classSectionId) { this.classSectionId = classSectionId; }

        public UUID getTeacherId() { return teacherId; }
        public void setTeacherId(UUID teacherId) { this.teacherId = teacherId; }

        public String getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public Integer getPeriodNumber() { return periodNumber; }
        public void setPeriodNumber(Integer periodNumber) { this.periodNumber = periodNumber; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public String getSubjectName() { return subjectName; }
        public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

        public String getRoomNumber() { return roomNumber; }
        public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    }
}
