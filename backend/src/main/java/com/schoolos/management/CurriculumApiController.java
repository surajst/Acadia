package com.schoolos.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/curriculum")
public class CurriculumApiController {

    private final CurriculumService curriculumService;

    @Autowired
    public CurriculumApiController(CurriculumService curriculumService) {
        this.curriculumService = curriculumService;
    }

    @GetMapping
    public ResponseEntity<List<Curriculum>> getTopics(
            @RequestParam("syllabus") SyllabusType syllabus,
            @RequestParam("standard") int standard,
            @RequestParam(value = "subject", required = false) SubjectType subject) {
        
        List<Curriculum> topics = curriculumService.getTopics(syllabus, standard, subject);
        return ResponseEntity.ok(topics);
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectType>> getSubjects(
            @RequestParam("syllabus") SyllabusType syllabus,
            @RequestParam("standard") int standard) {
        
        List<SubjectType> subjects = curriculumService.getSubjects(syllabus, standard);
        return ResponseEntity.ok(subjects);
    }
}
