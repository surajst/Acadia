package com.concept.student.app;

import com.concept.academics.MathSkill;
import com.concept.academics.MathSkillRepository;
import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.shared.data.AcademicSubmission;
import com.concept.shared.data.AcademicSubmissionRepository;
import com.concept.shared.data.Attendance;
import com.concept.shared.data.AttendanceRepository;
import com.concept.curriculum.data.Curriculum;
import com.concept.curriculum.app.CurriculumService;
import com.concept.parent.data.ParentQuest;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentReward;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.rewards.data.RewardItem;
import com.concept.rewards.data.RewardItemRepository;
import com.concept.shared.data.Student;
import com.concept.oversight.data.StudentProgress;
import com.concept.oversight.data.StudentProgressRepository;
import com.concept.oversight.app.StudentProgressService;
import com.concept.shared.data.StudentRepository;
import com.concept.curriculum.data.SyllabusType;
import com.concept.tasks.app.TeacherTaskService;
import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the student-facing surface (mobile app + student portal
 * actions). Owns student resolution, XP math, curriculum progress, and the
 * quest/reward ownership checks so the web controllers only bind and shape
 * responses (ADR 0001). Every quest/reward a request touches is verified to
 * belong to the caller's own student record — the cross-student IDOR class is
 * handled here.
 */
// Explicit bean name avoids a collision with the legacy management.StudentService,
// which still exists until that package is fully carved. Injection is by type,
// so controllers are unaffected.
@Service("studentSliceService")
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final AcademicSubmissionRepository academicSubmissionRepository;
    private final MathSkillRepository mathSkillRepository;
    private final RewardItemRepository rewardItemRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final AttendanceRepository attendanceRepository;
    private final TeacherTaskService teacherTaskService;
    private final StudentProgressRepository studentProgressRepository;
    private final CurriculumService curriculumService;
    private final StudentProgressService studentProgressService;
    private final CurrentUserService currentUserService;

    public StudentService(StudentRepository studentRepository,
                          StudentMetricRepository studentMetricRepository,
                          AcademicSubmissionRepository academicSubmissionRepository,
                          MathSkillRepository mathSkillRepository,
                          RewardItemRepository rewardItemRepository,
                          ParentRewardRepository parentRewardRepository,
                          ParentQuestRepository parentQuestRepository,
                          AttendanceRepository attendanceRepository,
                          TeacherTaskService teacherTaskService,
                          StudentProgressRepository studentProgressRepository,
                          CurriculumService curriculumService,
                          StudentProgressService studentProgressService,
                          CurrentUserService currentUserService) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.academicSubmissionRepository = academicSubmissionRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.rewardItemRepository = rewardItemRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.attendanceRepository = attendanceRepository;
        this.teacherTaskService = teacherTaskService;
        this.studentProgressRepository = studentProgressRepository;
        this.curriculumService = curriculumService;
        this.studentProgressService = studentProgressService;
        this.currentUserService = currentUserService;
    }

    private Student requireStudent(Authentication authentication) {
        return currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> StudentException.badRequest("Student record not found"));
    }

    // ─── Mobile dashboard / reads ───────────────────────────────────────────

    public Map<String, Object> mobileDashboard(Authentication authentication) {
        Student student = requireStudent(authentication);
        UUID studentId = student.getId();

        StudentMetric metrics = studentMetricRepository.findByStudentId(studentId).orElse(new StudentMetric());
        int totalXp = metrics.getSchoolXp() != null ? metrics.getSchoolXp() : 0;
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

        Map<String, Object> metricsMap = new HashMap<>();
        metricsMap.put("schoolXp", metrics.getSchoolXp());
        metricsMap.put("parentXp", metrics.getParentXp());
        metricsMap.put("activeStreak", metrics.getActiveStreak());
        metricsMap.put("scholarLevel", scholarLevel);
        metricsMap.put("levelProgress", levelProgress);
        metricsMap.put("xpToNextLevel", xpToNextLevel);
        metricsMap.put("totalXp", totalXp);
        response.put("metrics", metricsMap);

        response.put("submissions", submissions);
        response.put("availableSkills", availableSkills);
        response.put("rewardInventoryList", rewardInventoryList);
        response.put("pendingParentRewards", pendingParentRewards);
        response.put("parentQuests", parentQuests);
        response.put("availableParentRewards", availableParentRewards);

        return response;
    }

    public List<Map<String, String>> mobileAttendance(Authentication authentication) {
        Student student = requireStudent(authentication);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(60);
        return attendanceRepository.findByStudentAndAttendanceDateBetween(student, startDate, endDate).stream()
                .sorted(Comparator.comparing(Attendance::getAttendanceDate))
                .map(a -> {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("date", a.getAttendanceDate().toString());
                    entry.put("status", a.getStatus().name());
                    return entry;
                })
                .collect(Collectors.toList());
    }

    public Object mobileTasks(Authentication authentication) {
        Student student = requireStudent(authentication);
        return teacherTaskService.getTasksForStudent(student.getId(), extractStandard(student), student.getTenantId());
    }

    public List<Map<String, Object>> mobileSyllabus(Authentication authentication) {
        Student student = requireStudent(authentication);
        int standard = extractStandard(student);
        List<Curriculum> allTopics = curriculumService.getTopics(student.getTenantId(), SyllabusType.CBSE, standard, null);
        List<StudentProgress> progressList = studentProgressRepository.findByStudentId(student.getId());
        Set<UUID> completedIds = progressList.stream()
                .filter(StudentProgress::isCompleted)
                .map(p -> p.getCurriculum().getId())
                .collect(Collectors.toSet());
        return allTopics.stream().map(topic -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", topic.getId());
            entry.put("topicName", topic.getTopicName());
            entry.put("subjectType", topic.getSubjectCode());
            entry.put("standard", topic.getStandard());
            entry.put("xpReward", topic.getXpReward());
            entry.put("topicOrder", topic.getTopicOrder());
            entry.put("completed", completedIds.contains(topic.getId()));
            return entry;
        }).collect(Collectors.toList());
    }

    // ─── /api/student portal actions ────────────────────────────────────────

    @Transactional
    public Map<String, Object> completeSkill(UUID skillId, Authentication authentication) {
        Student student = requireStudent(authentication);
        MathSkill skill = mathSkillRepository.findByIdAndTenantId(skillId, student.getTenantId())
                .orElseThrow(() -> StudentException.badRequest("Skill not found"));
        StudentMetric metric = studentMetricRepository.findByStudentId(student.getId()).orElseThrow();
        metric.setSchoolXp(metric.getSchoolXp() + skill.getMaxXpReward());
        studentMetricRepository.save(metric);
        return Map.of("newXp", metric.getSchoolXp());
    }

    @Transactional
    public Map<String, Object> claimQuestApi(UUID questId, Authentication authentication) {
        Student student = requireStudent(authentication);
        ParentQuest quest = parentQuestRepository.findByIdAndTenantId(questId, student.getTenantId())
                .orElseThrow(() -> StudentException.badRequest("Quest not found"));
        if (quest.getStudent() == null || !student.getId().equals(quest.getStudent().getId())) {
            throw StudentException.forbidden("Not authorized for this quest");
        }
        quest.setStatus("AWAITING_APPROVAL");
        parentQuestRepository.save(quest);
        return Map.of("status", "success");
    }

    @Transactional
    public Map<String, Object> confirmRewardReceived(UUID rewardId, Authentication authentication) {
        Student student = requireStudent(authentication);
        ParentReward reward = parentRewardRepository.findByIdAndTenantId(rewardId, student.getTenantId())
                .orElseThrow(() -> StudentException.badRequest("Reward not found"));
        if (reward.getStudent() == null || !student.getId().equals(reward.getStudent().getId())) {
            throw StudentException.forbidden("Not authorized for this reward");
        }
        reward.setStatus("FULLY_REDEEMED");
        parentRewardRepository.save(reward);
        return Map.of("status", "success");
    }

    // ─── /api/student/progress ──────────────────────────────────────────────

    public Object progress(Authentication authentication) {
        Student student = requireStudent(authentication);
        return studentProgressService.getProgressByStudent(student.getId(), student.getTenantId());
    }

    @Transactional
    public Object markProgressComplete(String curriculumIdStr, Authentication authentication) {
        if (curriculumIdStr == null || curriculumIdStr.isBlank()) {
            throw StudentException.badRequest("curriculumId is required");
        }
        UUID curriculumId;
        try {
            curriculumId = UUID.fromString(curriculumIdStr);
        } catch (IllegalArgumentException e) {
            throw StudentException.badRequest("Invalid curriculumId format");
        }
        Student student = requireStudent(authentication);
        return studentProgressService.markTopicComplete(student.getId(), curriculumId, student.getTenantId());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private int extractStandard(Student student) {
        try {
            String grade = student.getClassSection().getGradeName();
            return Integer.parseInt(grade.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 6;
        }
    }
}
