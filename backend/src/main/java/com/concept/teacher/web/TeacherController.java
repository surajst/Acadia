package com.concept.teacher.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy demo roster endpoint (static sample data). Kept for backward
 * compatibility with early mobile builds; carries no persistence, so it lives
 * entirely in the interface layer (ADR 0001).
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @GetMapping("/roster/class6-math")
    public List<Map<String, Object>> getClass6MathRoster() {
        return List.of(
            Map.of(
                "id", "33333333-3333-3333-3333-333333333331",
                "name", "Arnav Sharma",
                "rollNumber", "6A01"
            ),
            Map.of(
                "id", "33333333-3333-3333-3333-333333333332",
                "name", "Alisha Patel",
                "rollNumber", "6A02"
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "name", "Rohan Das",
                "rollNumber", "6A03"
            )
        );
    }
}
