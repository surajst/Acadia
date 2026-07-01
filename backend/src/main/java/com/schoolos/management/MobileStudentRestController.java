package com.schoolos.management;

import com.schoolos.academics.MathSkill;
import com.schoolos.academics.MathSkillRepository;
import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.management.Attendance;
import com.schoolos.management.AttendanceRepository;
import com.schoolos.user.UserRepository;
import com.schoolos.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schoolos.management.StudentProgress;
import com.schoolos.management.StudentProgressRepository;
import com.schoolos.management.CurriculumService;
import com.schoolos.management.Curriculum;
import com.schoolos.management.SyllabusType;

@RestController
@RequestMapping("/api/mobile/student")
public class MobileStudentRestController {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private AcademicSubmissionRepository academicSubmissionRepository;

    @Autowired
    private MathSkillRepository mathSkillRepository;

    @Autowired
    private RewardItemRepository rewardItemRepository;

    @Autowired
    private ParentRewardRepository parentRewardRepository;

    @Autowired
    private ParentQuestRepository parentQuestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TeacherTaskService teacherTaskService;

    @Autowired
    private StudentProgressRepository studentProgressRepository;

    @Autowired
    private CurriculumService curriculumService;

    private Student resolveStudent(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is null or empty");
        }
        
        String searchName = username;
        
        // If it's an email (from our new DB UserDetailsService), look up the User first
        if (username.contains("@")) {
            User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
            searchName = user.getFullName().split(" ")[0];
        }

        if (searchName.startsWith("student_")) {
            String suffix = searchName.substring(8);
            for (Student s : studentRepository.findAll()) {
                if (("Pilot-" + suffix).equals(s.getRollNumber())) {
                    return s;
                }
            }
        }
        
        final String finalSearchName = searchName;
        return studentRepository.findByFirstNameIgnoreCase(finalSearchName)
            .orElseThrow(() -> new IllegalArgumentException("Student record not found for name: " + finalSearchName));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "arjun";
        Student student = resolveStudent(username);
        UUID studentId = student.getId();

        StudentMetric studentMetrics = studentMetricRepository.findByStudentId(studentId).orElse(new StudentMetric());

        int totalXp = studentMetrics.getSchoolXp() != null ? studentMetrics.getSchoolXp() : 0;
        int scholarLevel = (totalXp / 500) + 1;
        int levelProgress = (totalXp % 500) * 100 / 500;
        int xpToNextLevel = 500 - (totalXp % 500);

        List<AcademicSubmission> submissions = academicSubmissionRepository.findByStudentId(studentId);
        List<MathSkill> availableSkills = mathSkillRepository.findAll();
        List<RewardItem> rewardInventoryList = rewardItemRepository.findAll();
        
        List<ParentReward> pendingParentRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "PENDING");
        List<ParentQuest> parentQuests = parentQuestRepository.findByStudentId(studentId);
        List<ParentReward> availableParentRewards = parentRewardRepository.findByStudentIdAndStatus(studentId, "AVAILABLE");

        Map<String, Object> response = new HashMap<>();
        
        // Basic Info
        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("id", student.getId());
        studentInfo.put("firstName", student.getFirstName());
        studentInfo.put("lastName", student.getLastName());
        studentInfo.put("rollNumber", student.getRollNumber());
        if (student.getClassSection() != null) {
            studentInfo.put("gradeName", student.getClassSection().getGradeName());
            studentInfo.put("sectionName", student.getClassSection().getSectionName());
        }
        response.put("student", studentInfo);

        // Metrics
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("schoolXp", studentMetrics.getSchoolXp());
        metrics.put("parentXp", studentMetrics.getParentXp());
        metrics.put("activeStreak", studentMetrics.getActiveStreak());
        metrics.put("scholarLevel", scholarLevel);
        metrics.put("levelProgress", levelProgress);
        metrics.put("xpToNextLevel", xpToNextLevel);
        metrics.put("totalXp", totalXp);
        response.put("metrics", metrics);

        // Lists
        response.put("submissions", submissions);
        response.put("availableSkills", availableSkills);
        response.put("rewardInventoryList", rewardInventoryList);
        response.put("pendingParentRewards", pendingParentRewards);
        response.put("parentQuests", parentQuests);
        response.put("availableParentRewards", availableParentRewards);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/attendance")
    public ResponseEntity<?> getAttendanceLog(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "arjun";
        Student student = resolveStudent(username);
        
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(60);
        
        List<Map<String, String>> log = attendanceRepository.findByStudentAndAttendanceDateBetween(student, startDate, endDate).stream()
                .sorted(java.util.Comparator.comparing(Attendance::getAttendanceDate))
                .map(a -> {
                    Map<String, String> entry = new java.util.HashMap<>();
                    entry.put("date", a.getAttendanceDate().toString());
                    entry.put("status", a.getStatus().name());
                    return entry;
                })
                .collect(java.util.stream.Collectors.toList());
                
        return ResponseEntity.ok(log);
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> getStudentTasks(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "arjun";
        Student student = resolveStudent(username);
        int standard = extractStandard(student);
        return ResponseEntity.ok(teacherTaskService.getTasksForStudent(student.getId(), standard));
    }

    @GetMapping("/syllabus")
    public ResponseEntity<?> getStudentSyllabus(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "arjun";
        Student student = resolveStudent(username);
        UUID studentId = student.getId();
        int standard = extractStandard(student);
        List<Curriculum> allTopics = curriculumService.getTopics(SyllabusType.CBSE, standard, null);
        List<StudentProgress> progressList = studentProgressRepository.findByStudentId(studentId);
        Set<UUID> completedIds = progressList.stream()
                .filter(StudentProgress::isCompleted)
                .map(p -> p.getCurriculum().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> result = allTopics.stream().map(topic -> {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", topic.getId());
            entry.put("topicName", topic.getTopicName());
            entry.put("subjectType", topic.getSubjectType().name());
            entry.put("standard", topic.getStandard());
            entry.put("xpReward", topic.getXpReward());
            entry.put("topicOrder", topic.getTopicOrder());
            entry.put("completed", completedIds.contains(topic.getId()));
            return entry;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
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
