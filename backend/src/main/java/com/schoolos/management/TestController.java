package com.schoolos.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class TestController {

    @Autowired
    private ParentQuestRepository parentQuestRepo;
    
    @Autowired
    private ClassSectionRepository classSectionRepo;
    
    @Autowired
    private StudentRepository studentRepository;

    @GetMapping("/public-test-students")
    public Map<String, Object> testStudents() {
        try {
            List<Map<String, Object>> sectionData = classSectionRepo.findAll().stream().map(s -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", s.getId());
                m.put("teacherId", s.getTeacherId());
                m.put("tenantId", s.getTenantId());
                return m;
            }).collect(Collectors.toList());
            
            List<Map<String, Object>> studentData = studentRepository.findAll().stream().map(s -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", s.getId());
                m.put("name", s.getFirstName());
                m.put("classId", s.getClassSection() != null ? s.getClassSection().getId() : null);
                return m;
            }).collect(Collectors.toList());
            
            List<Map<String, Object>> quests = parentQuestRepo.findAll().stream().map(q -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", q.getId());
                m.put("studentId", q.getStudent().getId());
                m.put("desc", q.getTaskDescription());
                return m;
            }).collect(Collectors.toList());

            return Map.of("sections", sectionData, "students", studentData, "quests", quests);
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
