package com.schoolos.management;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/progress")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProgressApiController {

    private final AdminProgressService adminProgressService;

    public AdminProgressApiController(AdminProgressService adminProgressService) {
        this.adminProgressService = adminProgressService;
    }

    @GetMapping("/school")
    public ResponseEntity<Map<String, Object>> getSchoolWideProgress() {
        return ResponseEntity.ok(adminProgressService.getSchoolWideProgress());
    }

    @GetMapping("/class")
    public ResponseEntity<Map<String, Object>> getClassProgress(@RequestParam("standard") int standard) {
        return ResponseEntity.ok(adminProgressService.getClassProgress(standard));
    }
}
