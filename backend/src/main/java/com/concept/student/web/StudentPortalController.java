package com.concept.student.web;

import com.concept.student.app.StudentPortalPageService;
import com.concept.student.app.StudentPortalPageService.RedeemResult;
import com.concept.student.app.StudentPortalPageService.StudentPortalView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Interface layer for the student portal page. Thin binding over
 * {@link StudentPortalPageService}; all querying, entity-to-view flattening,
 * and reward mutations live in the application layer (ADR 0001).
 */
@Controller
public class StudentPortalController {

    private final StudentPortalPageService studentPortalPageService;

    public StudentPortalController(StudentPortalPageService studentPortalPageService) {
        this.studentPortalPageService = studentPortalPageService;
    }

    private String resolveRole(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if (authority.startsWith("ROLE_")) return authority.substring(5);
            }
        }
        return "STUDENT";
    }

    @GetMapping("/web/student/portal")
    public String getStudentPortal(@RequestParam(value = "tab", required = false, defaultValue = "dashboard") String activeTab,
                                   Model model, Authentication authentication) {
        StudentPortalView v = studentPortalPageService.dashboard(authentication);

        model.addAttribute("activeTab", activeTab);
        model.addAttribute("student", v.student());
        model.addAttribute("studentMetrics", v.studentMetrics());
        model.addAttribute("totalXp", v.totalXp());
        model.addAttribute("scholarLevel", v.scholarLevel());
        model.addAttribute("levelProgress", v.levelProgress());
        model.addAttribute("xpToNextLevel", v.xpToNextLevel());
        model.addAttribute("submissions", v.submissions());
        model.addAttribute("availableSkills", v.availableSkills());
        model.addAttribute("rewardInventoryList", v.rewardInventory());
        model.addAttribute("parent_rewards", v.pendingRewards());
        model.addAttribute("pendingRewardTitles", v.pendingRewardTitles());
        model.addAttribute("parentQuests", v.parentQuests());
        model.addAttribute("parentRewards", v.availableParentRewards());
        model.addAttribute("currentDate", LocalDate.now());
        model.addAttribute("systemScope", "STUDENT_PORTAL");
        model.addAttribute("currentUserRole", resolveRole(authentication));
        return "student_portal";
    }

    @PostMapping("/web/student/submit-milestone")
    public String submitMilestone(@RequestParam("skillName") String skillName,
                                  @RequestParam("proofOfWorkNotes") String proofOfWorkNotes,
                                  @RequestParam(value = "answer1", required = false) String answer1,
                                  @RequestParam(value = "answer2", required = false) String answer2,
                                  @RequestParam(value = "answer3", required = false) String answer3,
                                  @RequestParam(value = "teacherTaskId", required = false) UUID teacherTaskId,
                                  Authentication authentication) {
        studentPortalPageService.submitMilestone(skillName, proofOfWorkNotes, answer1, answer2, answer3,
                teacherTaskId, authentication);
        return "redirect:/web/student/portal?success=true";
    }

    @Transactional
    @PostMapping("/web/student/rewards/redeem")
    public String redeemReward(@RequestParam("rewardId") UUID rewardId, Authentication authentication) {
        RedeemResult outcome = studentPortalPageService.redeemReward(rewardId, authentication);
        if (outcome == RedeemResult.INSUFFICIENT_XP) {
            return "redirect:/web/student/portal?tab=rewards&error=insufficient_xp";
        }
        if (outcome == RedeemResult.NO_LINKED_PARENT) {
            return "redirect:/web/student/portal?tab=rewards&error=no_linked_parent";
        }
        return "redirect:/web/student/portal?tab=rewards&success=redeemed";
    }

    @PostMapping("/web/student/quest/{id}/claim")
    public String claimQuest(@PathVariable("id") UUID id, Authentication authentication) {
        studentPortalPageService.claimQuest(id, authentication);
        return "redirect:/web/student/portal?success=quest_claimed";
    }

    @PostMapping("/web/student/reward/{id}/redeem")
    public String redeemParentReward(@PathVariable("id") UUID id, Authentication authentication) {
        RedeemResult outcome = studentPortalPageService.redeemParentReward(id, authentication);
        if (outcome == RedeemResult.INSUFFICIENT_XP) {
            return "redirect:/web/student/portal?tab=rewards&error=insufficient_xp";
        }
        return "redirect:/web/student/portal?tab=rewards&success=reward_redeemed";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public String handleException(Exception ex, Model model) {
        System.err.println("--- STUDENT PORTAL CONTROLLER EXCEPTION DETECTED ---");
        ex.printStackTrace();
        java.io.StringWriter sw = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(sw));
        return "GLOBAL_EXCEPTION: " + ex.getMessage() + "\n" + sw.toString();
    }
}
