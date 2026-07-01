package com.schoolos.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class TeacherTaskApiController {

    @Autowired
    private TeacherTaskService teacherTaskService;
    
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ClassSectionRepository classSectionRepo;

    @Autowired
    private ParentQuestRepository parentQuestRepo;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @PostMapping("/teacher/tasks/create")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> createTask(@RequestBody TeacherTaskRequest request, Authentication authentication) {
        try {
            String username = authentication != null ? authentication.getName() : "teacher_1";
            System.err.println("--- CREATE TASK ---");
            System.err.println("Username from auth: " + username);
            UUID teacherId = teacherTaskService.resolveTeacherId(username);
            System.err.println("Resolved Teacher ID: " + teacherId);
            TeacherTask task = teacherTaskService.createTask(request, username);
            System.err.println("Task created with ID: " + task.getId());
            return ResponseEntity.ok(task);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/teacher/my-students")
    public ResponseEntity<List<Map<String, String>>> searchMyStudents(
            @RequestParam(value = "q", defaultValue = "") String query,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "teacher_1";
        if ("teacher@greenwood.com".equalsIgnoreCase(username)) {
            username = "teacher_1";
        }
        UUID teacherId = UUID.nameUUIDFromBytes(username.getBytes());
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // Fixed tenant
        
        List<ClassSection> sections = classSectionRepo.findByTeacherIdAndTenantId(teacherId, tenantId);
        List<Student> students = studentRepository.findByClassSectionIn(sections);
        System.err.println("--- SEARCH MY STUDENTS ---");
        System.err.println("teacherId=" + teacherId + ", tenantId=" + tenantId);
        System.err.println("sections size=" + sections.size() + ", students size=" + students.size());
        if (students.size() > 0) {
            System.err.println("First student: " + students.get(0).getFirstName() + " " + students.get(0).getLastName());
        }
        
        String lowerQuery = query.toLowerCase();
        List<Map<String, String>> result = students.stream()
            .filter(s -> (s.getFirstName() + " " + s.getLastName()).toLowerCase().contains(lowerQuery))
            .map(s -> {
                String className = s.getClassSection() != null ? s.getClassSection().getGradeName() + " - " + s.getClassSection().getSectionName() : "Unknown";
                return Map.of(
                    "id", s.getId().toString(),
                    "name", s.getFirstName() + " " + s.getLastName(),
                    "className", className
                );
            })
            .collect(Collectors.toList());
            
        return ResponseEntity.ok(result);
    }

    @GetMapping("/teacher/test-students")
    public ResponseEntity<?> testStudents() {
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

            return ResponseEntity.ok(Map.of("sections", sectionData, "students", studentData, "quests", quests));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(e.getMessage() + " \n " + e.toString());
        }
    }

    @GetMapping("/teacher/tasks/my-tasks")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<?> getMyTasks(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "teacher_1";
        System.err.println("--- GET MY TASKS ---");
        System.err.println("Username from auth: " + username);
        UUID teacherId = teacherTaskService.resolveTeacherId(username);
        System.err.println("Resolved Teacher ID: " + teacherId);
        List<TeacherTask> tasks = teacherTaskService.getTasksCreatedByTeacher(username);
        System.err.println("Found tasks: " + tasks.size());
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/student/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getStudentAttendance(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "student_1";
        Student student = resolveStudent(username);
        LocalDate start = LocalDate.now().minusDays(60);
        LocalDate end = LocalDate.now();
        List<Map<String, Object>> records = attendanceRepository
                .findByStudentAndAttendanceDateBetween(student, start, end)
                .stream()
                .sorted((a, b) -> b.getAttendanceDate().compareTo(a.getAttendanceDate()))
                .map(a -> Map.<String, Object>of(
                        "date", a.getAttendanceDate().toString(),
                        "status", a.getStatus().name(),
                        "dayOfWeek", a.getAttendanceDate().getDayOfWeek().toString()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(records);
    }

    @GetMapping("/student/tasks")
    @PreAuthorize("hasAnyRole('STUDENT')")
    public ResponseEntity<?> getStudentTasks(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "student_1";
        Student student = resolveStudent(username);
        int standard = extractStandard(student);
        return ResponseEntity.ok(teacherTaskService.getTasksForStudent(student.getId(), standard));
    }

    @GetMapping("/student/tasks/{taskId}/questions")
    @PreAuthorize("hasAnyRole('STUDENT')")
    public ResponseEntity<?> getTaskQuestions(@PathVariable UUID taskId) {
        return ResponseEntity.ok(teacherTaskService.getQuestionsForTask(taskId));
    }

    private Student resolveStudent(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is null or empty");
        }
        
        String searchName = username;
        if (username.contains("@")) {
            searchName = username.substring(0, username.indexOf("@"));
        }

        if (searchName.startsWith("student_")) {
            String suffix = searchName.substring(8);
            for (Student s : studentRepository.findAll()) {
                if (("Pilot-" + suffix).equals(s.getRollNumber())) {
                    return s;
                }
            }
        }
        return studentRepository.findByFirstNameIgnoreCase(searchName)
            .orElseThrow(() -> new IllegalArgumentException("Student record not found for username: " + username));
    }
    
    private int extractStandard(Student student) {
        try {
            String grade = student.getClassSection().getGradeName(); 
            return Integer.parseInt(grade.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 6;
        }
    }
}
