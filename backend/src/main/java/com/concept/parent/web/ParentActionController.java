package com.concept.parent.web;

import com.concept.parent.app.ParentService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Interface layer for the parent web portal's write actions (approve/hold/
 * release rewards, assign tasks, approve quests). Binds form posts and maps
 * outcomes to redirects; all logic and ownership checks live in
 * {@link ParentService} (ADR 0001).
 */
@Controller
public class ParentActionController {

    private final ParentService parentService;

    public ParentActionController(ParentService parentService) {
        this.parentService = parentService;
    }

    @GetMapping("/web/parent/portal")
    public String portal() {
        return "redirect:/web/parent/dashboard";
    }

    @PostMapping("/web/parent/reward/{id}/approve")
    public String approveReward(@PathVariable("id") UUID id, Authentication authentication) {
        parentService.approveReward(id, authentication);
        return "redirect:/web/parent/dashboard?success=approved";
    }

    @PostMapping("/web/parent/reward/{id}/hold")
    public String holdReward(@PathVariable("id") UUID id, Authentication authentication) {
        parentService.holdReward(id, authentication);
        return "redirect:/web/parent/dashboard?success=held";
    }

    @PostMapping("/web/parent/assign-task")
    public String assignTask(@RequestParam("studentId") UUID studentId,
                             @RequestParam("taskDescription") String taskDescription,
                             @RequestParam("xpBounty") Integer xpBounty,
                             Authentication authentication) {
        parentService.assignTask(studentId, taskDescription, xpBounty, authentication);
        return "redirect:/web/parent/dashboard?success=task_assigned";
    }

    @PostMapping("/web/parent/add-reward")
    public String addReward(@RequestParam("studentId") UUID studentId,
                            @RequestParam("rewardTitle") String rewardTitle,
                            @RequestParam("xpCost") Integer xpCost,
                            Authentication authentication) {
        parentService.addReward(studentId, rewardTitle, xpCost, authentication);
        return "redirect:/web/parent/dashboard?success=reward_added";
    }

    @PostMapping("/web/parent/quest/{id}/approve")
    public String approveQuest(@PathVariable("id") UUID id, Authentication authentication) {
        parentService.approveQuestWeb(id, authentication);
        return "redirect:/web/parent/dashboard?success=quest_approved";
    }

    @PostMapping("/web/parent/reward/{id}/release")
    public String releaseReward(@PathVariable("id") UUID id, Authentication authentication) {
        parentService.releaseReward(id, authentication);
        return "redirect:/web/parent/dashboard?success=reward_released";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, RedirectAttributes ra) {
        ra.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/web/parent/dashboard";
    }
}
