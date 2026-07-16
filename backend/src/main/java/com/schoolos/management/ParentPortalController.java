package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class ParentPortalController {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private AcademicSubmissionRepository academicSubmissionRepository;

    @Autowired
    private ParentRewardRepository parentRewardRepository;

    @Autowired
    private ParentQuestRepository parentQuestRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private StudentProgressService studentProgressService;

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * Resolves the actual logged-in parent via CurrentUserService (User ->
     * Parent by userId FK). Replaces a prior fuzzy firstName/email-prefix
     * heuristic that fell back to "the first parent named Rajesh or Ramesh,
     * or just the first parent in the tenant" when no match was found — that
     * heuristic could silently resolve to the wrong parent, which would have
     * defeated any ownership check built on top of it.
     */
    private Parent resolveParent(Authentication authentication) {
        return currentUserService.getCurrentParent(authentication).orElse(null);
    }

    private void assertOwnsQuestsStudent(ParentQuest quest, Authentication authentication) {
        Parent parent = resolveParent(authentication);
        if (parent == null || !quest.getStudent().getParents().contains(parent)) {
            throw new IllegalArgumentException("Not authorized for this quest");
        }
    }

    private void assertOwnsRewardsStudent(ParentReward reward, Authentication authentication) {
        Parent parent = resolveParent(authentication);
        if (parent == null || !reward.getStudent().getParents().contains(parent)) {
            throw new IllegalArgumentException("Not authorized for this reward");
        }
    }

    @GetMapping("/web/parent/portal")
    public String getParentPortal() {
        return "redirect:/web/parent/dashboard";
    }

    @Transactional
    @PostMapping("/web/parent/reward/{id}/approve")
    public String approveReward(@PathVariable("id") UUID id, Authentication authentication) {
        try {
            ParentReward reward = parentRewardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parent reward ID: " + id));
            assertOwnsRewardsStudent(reward, authentication);

            reward.setStatus("APPROVED");
            parentRewardRepository.saveAndFlush(reward);
        } catch (Exception e) {
            throw new RuntimeException("Parent reward approval failed: " + e.getMessage(), e);
        }

        return "redirect:/web/parent/dashboard?success=approved";
    }

    @Transactional
    @PostMapping("/web/parent/reward/{id}/hold")
    public String holdReward(@PathVariable("id") UUID id, Authentication authentication) {
        try {
            ParentReward reward = parentRewardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parent reward ID: " + id));
            assertOwnsRewardsStudent(reward, authentication);

            reward.setStatus("HELD");
            parentRewardRepository.saveAndFlush(reward);
        } catch (Exception e) {
            throw new RuntimeException("Parent reward hold failed: " + e.getMessage(), e);
        }

        return "redirect:/web/parent/dashboard?success=held";
    }

    @Transactional
    @PostMapping("/web/parent/assign-task")
    public String assignTask(@RequestParam("studentId") UUID studentId,
                             @RequestParam("taskDescription") String taskDescription,
                             @RequestParam("xpBounty") Integer xpBounty,
                             Authentication authentication) {
        try {
            Parent parent = resolveParent(authentication);
            if (parent == null) {
                throw new IllegalArgumentException("No parent record found");
            }

            Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid student ID"));
            if (!student.getParents().contains(parent)) {
                throw new IllegalArgumentException("Not authorized for this student");
            }

            ParentQuest quest = new ParentQuest();
            quest.setId(UUID.randomUUID());
            quest.setParent(parent);
            quest.setStudent(student);
            quest.setTaskDescription(taskDescription);
            quest.setXpBounty(xpBounty);
            quest.setStatus("PENDING");
            quest.setTenantId(parent.getTenantId());
            quest.setAcademicYearId(parent.getAcademicYearId());
            
            System.err.println("--- ASSIGN TASK ---");
            System.err.println("Student ID=" + studentId + " parent ID=" + parent.getId() + " desc=" + taskDescription);
            parentQuestRepository.saveAndFlush(quest);
        } catch (Exception e) {
            throw new RuntimeException("Assign task failed: " + e.getMessage(), e);
        }
        return "redirect:/web/parent/dashboard?success=task_assigned";
    }

    @Transactional
    @PostMapping("/web/parent/add-reward")
    public String addReward(@RequestParam("studentId") UUID studentId,
                            @RequestParam("rewardTitle") String rewardTitle,
                            @RequestParam("xpCost") Integer xpCost,
                            Authentication authentication) {
        try {
            Parent parent = resolveParent(authentication);
            if (parent == null) {
                throw new IllegalArgumentException("No parent record found");
            }

            Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid student ID"));
            if (!student.getParents().contains(parent)) {
                throw new IllegalArgumentException("Not authorized for this student");
            }

            ParentReward reward = new ParentReward();
            reward.setId(UUID.randomUUID());
            reward.setParent(parent);
            reward.setStudent(student);
            reward.setRewardTitle(rewardTitle);
            reward.setXpCost(xpCost);
            reward.setStatus("AVAILABLE");
            reward.setTenantId(student.getTenantId());
            reward.setAcademicYearId(student.getAcademicYearId());

            parentRewardRepository.saveAndFlush(reward);
        } catch (Exception e) {
            throw new RuntimeException("Add reward failed: " + e.getMessage(), e);
        }
        return "redirect:/web/parent/dashboard?success=reward_added";
    }

    @Transactional
    @PostMapping("/web/parent/quest/{id}/approve")
    public String approveQuest(@PathVariable("id") UUID id, Authentication authentication) {
        try {
            ParentQuest quest = parentQuestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parent quest ID: " + id));
            assertOwnsQuestsStudent(quest, authentication);

            quest.setStatus("APPROVED");
            parentQuestRepository.saveAndFlush(quest);

            // Fetch and update student's Parent XP metrics
            UUID studentId = quest.getStudent().getId();
            StudentMetric metric = studentMetricRepository.findByStudentId(studentId).orElse(null);
            if (metric == null) {
                metric = new StudentMetric();
                metric.setId(UUID.randomUUID());
                metric.setStudent(quest.getStudent());
                metric.setTenantId(quest.getStudent().getTenantId());
                metric.setAcademicYearId(quest.getStudent().getAcademicYearId());
                metric.setSchoolXp(0);
                metric.setParentXp(0);
                metric.setActiveStreak(0);
            }
            int currentParentXp = metric.getParentXp() != null ? metric.getParentXp() : 0;
            metric.setParentXp(currentParentXp + quest.getXpBounty());
            studentMetricRepository.saveAndFlush(metric);

        } catch (Exception e) {
            throw new RuntimeException("Parent quest approval failed: " + e.getMessage(), e);
        }
        return "redirect:/web/parent/dashboard?success=quest_approved";
    }

    @Transactional
    @PostMapping("/web/parent/reward/{id}/release")
    public String releaseReward(@PathVariable("id") UUID id, Authentication authentication) {
        try {
            ParentReward reward = parentRewardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid parent reward ID: " + id));
            assertOwnsRewardsStudent(reward, authentication);

            reward.setStatus("DELIVERED");
            parentRewardRepository.saveAndFlush(reward);
        } catch (Exception e) {
            throw new RuntimeException("Parent reward release failed: " + e.getMessage(), e);
        }
        return "redirect:/web/parent/dashboard?success=reward_released";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        ra.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/web/parent/dashboard";
    }

    @GetMapping("/api/parent/child-progress")
    @ResponseBody
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> getChildProgress(
            @RequestParam("studentId") UUID studentId,
            Authentication authentication) {
        
        Parent parent = resolveParent(authentication);
        if (parent == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No parent record found");
        }
        
        List<Student> linkedStudents = studentRepository.findByParentsContaining(parent);
        boolean isLinked = linkedStudents.stream().anyMatch(s -> s.getId().equals(studentId));
        if (!isLinked) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to view this student's progress");
        }
        
        return ResponseEntity.ok(studentProgressService.getProgressByStudent(studentId));
    }
}
