package com.schoolos.management;

import com.schoolos.common.NotificationDeliveryService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
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
    private final NotificationDeliveryService notificationDeliveryService;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final UserRepository userRepository;

    public AttendanceApiController(ClassSectionRepository classSectionRepository,
                                   StudentRepository studentRepository,
                                   AttendanceRepository attendanceRepository,
                                   NotificationDeliveryService notificationDeliveryService,
                                   SubjectAssignmentRepository subjectAssignmentRepository,
                                   UserRepository userRepository) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.userRepository = userRepository;
    }

    // A teacher may only touch attendance for a section they're actually
    // assigned to teach — same tenant is implied, since assignments are
    // never created across tenants.
    private boolean teacherOwnsSection(User teacher, ClassSection section) {
        return subjectAssignmentRepository.existsByTeacherAndClassSection(teacher, section);
    }

    // ── GET today's attendance for a class section ──────────────────────────
    @GetMapping("/attendance/today/{sectionId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getTodayAttendance(@PathVariable UUID sectionId, Authentication authentication) {
        User teacher = userRepository.findByEmail(authentication.getName()).orElse(null);
        ClassSection section = classSectionRepository.findById(sectionId).orElse(null);
        if (teacher == null || section == null || !teacherOwnsSection(teacher, section)) {
            // Same message whether the section doesn't exist or isn't the
            // caller's — don't reveal that a section ID belongs to someone else.
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

        User teacher = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (teacher == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Teacher not found"));
        }

        LocalDate today = LocalDate.now();
        int saved = 0;
        int skipped = 0;

        for (AttendanceEntry entry : payload.attendance()) {
            Student student = studentRepository.findById(entry.studentId()).orElse(null);
            if (student == null) { skipped++; continue; }

            ClassSection section = student.getClassSection();
            if (section == null || !teacherOwnsSection(teacher, section)) { skipped++; continue; }

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
                    notificationDeliveryService.send(parent.getPhoneNumber(),
                            "[ALERT WHATSAPP DISPATCH] Sending to "
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