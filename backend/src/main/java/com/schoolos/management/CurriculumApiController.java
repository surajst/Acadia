package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/curriculum")
public class CurriculumApiController {

    private final CurriculumService curriculumService;
    private final CurrentUserService currentUserService;

    @Autowired
    public CurriculumApiController(CurriculumService curriculumService, CurrentUserService currentUserService) {
        this.curriculumService = curriculumService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<Curriculum>> getTopics(
            @RequestParam("syllabus") SyllabusType syllabus,
            @RequestParam("standard") int standard,
            @RequestParam(value = "subject", required = false) SubjectType subject,
            Authentication authentication) {

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<Curriculum> topics = curriculumService.getTopics(tenantId, syllabus, standard, subject);
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectType>> getSubjects(
            @RequestParam("syllabus") SyllabusType syllabus,
            @RequestParam("standard") int standard,
            Authentication authentication) {

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        List<SubjectType> subjects = curriculumService.getSubjects(tenantId, syllabus, standard);
        return ResponseEntity.ok(subjects);
    }
}
