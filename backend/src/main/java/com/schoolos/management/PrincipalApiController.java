package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only oversight API for PRINCIPAL (and ADMIN, who can preview the same
 * view). Every endpoint here is a thin facade over existing read-only
 * services — no new business logic, no write operations. Deliberately kept
 * under its own /api/principal/** prefix instead of touching /api/admin/**,
 * so this sprint doesn't need to revisit any existing ADMIN-gated endpoint.
 */
@RestController
@RequestMapping("/api/principal")
@PreAuthorize("hasAnyRole('ADMIN', 'PRINCIPAL')")
public class PrincipalApiController {

    private final AdminProgressService adminProgressService;
    private final FeeManagementService feeManagementService;
    private final AttendanceRepository attendanceRepository;
    private final CurrentUserService currentUserService;

    public PrincipalApiController(AdminProgressService adminProgressService,
                                   FeeManagementService feeManagementService,
                                   AttendanceRepository attendanceRepository,
                                   CurrentUserService currentUserService) {
        this.adminProgressService = adminProgressService;
        this.feeManagementService = feeManagementService;
        this.attendanceRepository = attendanceRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/progress/school")
    public ResponseEntity<Map<String, Object>> getSchoolProgress() {
        return ResponseEntity.ok(adminProgressService.getSchoolWideProgress());
    }

    @GetMapping("/progress/class")
    public ResponseEntity<Map<String, Object>> getClassProgress(@RequestParam int standard) {
        return ResponseEntity.ok(adminProgressService.getClassProgress(standard));
    }

    @GetMapping("/fee-summary")
    public ResponseEntity<Map<String, Object>> getFeeSummary(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return ResponseEntity.ok(feeManagementService.getSchoolWideFeeSummary(tenantId));
    }

    @GetMapping("/attendance-summary")
    public ResponseEntity<Map<String, Object>> getAttendanceSummary() {
        LocalDate today = LocalDate.now();
        long present = attendanceRepository.countByAttendanceDateAndStatus(today, AttendanceStatus.PRESENT);
        long absent = attendanceRepository.countByAttendanceDateAndStatus(today, AttendanceStatus.ABSENT);
        long total = present + absent;
        int attendancePercent = total == 0 ? 0 : (int) Math.round((double) present * 100 / total);

        Map<String, Object> summary = new HashMap<>();
        summary.put("date", today.toString());
        summary.put("present", present);
        summary.put("absent", absent);
        summary.put("attendancePercent", attendancePercent);
        return ResponseEntity.ok(summary);
    }
}
