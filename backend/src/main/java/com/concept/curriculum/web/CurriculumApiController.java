package com.concept.curriculum.web;

import com.concept.curriculum.app.CurriculumException;
import com.concept.curriculum.app.CurriculumQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Interface layer for the curriculum-topic catalog. Thin binding over
 * {@link CurriculumQueryService}; the syllabus enum is bound as a raw string
 * and resolved in the application layer so no domain type reaches web (ADR 0001).
 */
@RestController
@RequestMapping("/api/curriculum")
public class CurriculumApiController {

    private final CurriculumQueryService curriculumQueryService;

    public CurriculumApiController(CurriculumQueryService curriculumQueryService) {
        this.curriculumQueryService = curriculumQueryService;
    }

    @GetMapping
    public ResponseEntity<?> getTopics(
            @RequestParam("syllabus") String syllabus,
            @RequestParam("standard") int standard,
            @RequestParam(value = "subject", required = false) String subjectCode,
            Authentication authentication) {
        return ResponseEntity.ok(curriculumQueryService.topics(syllabus, standard, subjectCode, authentication));
    }

    @GetMapping("/subjects")
    public ResponseEntity<?> getSubjects(
            @RequestParam("syllabus") String syllabus,
            @RequestParam("standard") int standard,
            Authentication authentication) {
        return ResponseEntity.ok(curriculumQueryService.subjects(syllabus, standard, authentication));
    }

    @ExceptionHandler(CurriculumException.class)
    public ResponseEntity<?> handle(CurriculumException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}
