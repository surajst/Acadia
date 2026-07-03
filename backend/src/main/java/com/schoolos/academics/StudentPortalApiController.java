package com.schoolos.academics;

import com.schoolos.management.ParentQuest;
import com.schoolos.management.ParentQuestRepository;
import com.schoolos.management.ParentReward;
import com.schoolos.management.ParentRewardRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import com.schoolos.user.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/student")
public class StudentPortalApiController {

    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final MathSkillRepository mathSkillRepository;
    private final ParentQuestRepository parentQuestRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final CurrentUserService currentUserService;

    public StudentPortalApiController(StudentRepository studentRepository,
                                      StudentMetricRepository studentMetricRepository,
                                      MathSkillRepository mathSkillRepository,
                                      ParentQuestRepository parentQuestRepository,
                                      ParentRewardRepository parentRewardRepository,
                                      CurrentUserService currentUserService) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.currentUserService = currentUserService;
    }

    private Student resolveStudent(Authentication authentication) {
        return currentUserService.getCurrentStudent(authentication)
                .orElseThrow(() -> new RuntimeException("Student record not found"));
    }

    @PostMapping("/complete-skill/{id}")
    @Transactional
    public ResponseEntity<?> completeSkill(@PathVariable UUID id, Authentication authentication) {
        Student student = resolveStudent(authentication);

        Optional<MathSkill> skillOpt = mathSkillRepository.findById(id);
        if (skillOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Skill not found"));
        }
        MathSkill skill = skillOpt.get();

        StudentMetric metric = studentMetricRepository.findByStudentId(student.getId()).orElseThrow();
        metric.setSchoolXp(metric.getSchoolXp() + skill.getMaxXpReward());
        studentMetricRepository.save(metric);

        return ResponseEntity.ok(Map.of("newXp", metric.getSchoolXp()));
    }

    @PostMapping("/claim-quest/{id}")
    @Transactional
    public ResponseEntity<?> claimQuest(@PathVariable UUID id, Authentication authentication) {
        Student student = resolveStudent(authentication);

        Optional<ParentQuest> questOpt = parentQuestRepository.findById(id);
        if (questOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Quest not found"));
        }
        ParentQuest quest = questOpt.get();
        if (quest.getStudent() == null || !student.getId().equals(quest.getStudent().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this quest"));
        }
        quest.setStatus("AWAITING_APPROVAL");
        parentQuestRepository.save(quest);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PostMapping("/confirm-reward-received/{id}")
    @Transactional
    public ResponseEntity<?> confirmRewardReceived(@PathVariable UUID id, Authentication authentication) {
        Student student = resolveStudent(authentication);

        Optional<ParentReward> rewardOpt = parentRewardRepository.findById(id);
        if (rewardOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reward not found"));
        }
        ParentReward reward = rewardOpt.get();
        if (reward.getStudent() == null || !student.getId().equals(reward.getStudent().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not authorized for this reward"));
        }
        reward.setStatus("FULLY_REDEEMED");
        parentRewardRepository.save(reward);

        return ResponseEntity.ok(Map.of("status", "success"));
    }
}
