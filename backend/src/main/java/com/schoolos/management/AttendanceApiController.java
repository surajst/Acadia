package com.schoolos.management;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teacher")
public class AttendanceApiController {

    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;

    public AttendanceApiController(ClassSectionRepository classSectionRepository,
                                   StudentRepository studentRepository,
                                   AttendanceRepository attendanceRepository) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    // ── GET today's attendance for a class section ──────────────────────────
    @GetMapping("/attendance/today/{sectionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getTodayAttendance(@PathVariable UUID sectionId) {
        ClassSection section = classSectionRepository.findById(sectionId).orElse(null);
        if (section == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Section not found"));
        }

        LocalDate today = LocalDate.now();
        List<Attendance> records = attendanceRepository.findByClassSectionAndAttendanceDate(section, today);

        List<Map<String, Object>> result = records.stream().map(a -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("studentId", a.getStudent().getId());
            m.put("studentName", a.getStudent().getFirstName() + " " + a.getStudent().getLastName());
            m.put("status", a.getStatus());
            m.put("remarks", a.getRemarks());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── POST submit attendance for a class section ───────────────────────────
    @PostMapping("/attendance/submit")
    @PreAuthorize("hasRole('TEACHER')")
    @Transactional
    public ResponseEntity<?> submitAttendance(@RequestBody AttendancePayload payload,
                                              Authentication authentication) {
        if (payload == null || payload.attendance() == null || payload.attendance().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Attendance payload is required"));
        }

        LocalDate today = LocalDate.now();
        int saved = 0;
        int skipped = 0;

        for (AttendanceEntry entry : payload.attendance()) {
            Student student = studentRepository.findById(entry.studentId()).orElse(null);
            if (student == null) { skipped++; continue; }

            ClassSection section = student.getClassSection();
            if (section == null) { skipped++; continue; }

            // Prevent duplicate — delete existing record for today if present
            List<Attendance> existing = attendanceRepository
                    .findByClassSectionAndAttendanceDate(section, today)
                    .stream()
                    .filter(a -> a.getStudent().getId().equals(entry.studentId()))
                    .collect(Collectors.toList());
            if (!existing.isEmpty()) {
                attendanceRepository.deleteAll(existing);
            }

            Attendance attendance = new Attendance();
            attendance.setId(UUID.randomUUID());
            attendance.setTenantId(student.getTenantId());
            attendance.setAcademicYearId(student.getAcademicYearId());
            attendance.setStudent(student);
            attendance.setClassSection(section);
            attendance.setAttendanceDate(today);
            attendance.setStatus(entry.status());
            attendance.setRemarks(entry.remarks() != null ? entry.remarks() : "");
            attendanceRepository.save(attendance);
            saved++;

            if (entry.status() == AttendanceStatus.ABSENT) {
                for (Parent parent : student.getParents()) {
                    System.out.println("[ALERT WHATSAPP DISPATCH] Sending to "
                            + parent.getFirstName() + " " + parent.getLastName()
                            + " (" + parent.getPhoneNumber() + "): Alert! Student "
                            + student.getFirstName() + " was marked ABSENT today.");
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "saved", saved,
                "skipped", skipped
        ));
    }

    public static record AttendancePayload(List<AttendanceEntry> attendance) {}
    public static record AttendanceEntry(UUID studentId, AttendanceStatus status, String remarks) {}
}