package com.schoolos.academics;

import com.schoolos.management.ParentQuest;
import com.schoolos.management.ParentQuestRepository;
import com.schoolos.management.ParentReward;
import com.schoolos.management.ParentRewardRepository;
import com.schoolos.management.Student;
import com.schoolos.management.StudentRepository;
import org.springframework.http.ResponseEntity;
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

    public StudentPortalApiController(StudentRepository studentRepository,
                                      StudentMetricRepository studentMetricRepository,
                                      MathSkillRepository mathSkillRepository,
                                      ParentQuestRepository parentQuestRepository,
                                      ParentRewardRepository parentRewardRepository) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.mathSkillRepository = mathSkillRepository;
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
    }

    private Student getMockActiveStudent() {
        return studentRepository.findAll().stream()
                .filter(s -> s.getFirstName().equals("Aarav"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Student not found"));
    }

    @PostMapping("/complete-skill/{id}")
    @Transactional
    public ResponseEntity<?> completeSkill(@PathVariable UUID id) {
        Student student = getMockActiveStudent();
        
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
    public ResponseEntity<?> claimQuest(@PathVariable UUID id) {
        Optional<ParentQuest> questOpt = parentQuestRepository.findById(id);
        if (questOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Quest not found"));
        }
        ParentQuest quest = questOpt.get();
        quest.setStatus("AWAITING_APPROVAL");
        parentQuestRepository.save(quest);

        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PostMapping("/confirm-reward-received/{id}")
    @Transactional
    public ResponseEntity<?> confirmRewardReceived(@PathVariable UUID id) {
        Optional<ParentReward> rewardOpt = parentRewardRepository.findById(id);
        if (rewardOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reward not found"));
        }
        ParentReward reward = rewardOpt.get();
        reward.setStatus("FULLY_REDEEMED");
        parentRewardRepository.save(reward);

        return ResponseEntity.ok(Map.of("status", "success"));
    }
}
