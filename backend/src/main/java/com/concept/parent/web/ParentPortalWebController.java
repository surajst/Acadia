package com.concept.parent.web;

import com.concept.parent.app.ParentDashboardService;
import com.concept.parent.app.ParentDashboardService.ParentDashboardView;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * Interface layer for the parent dashboard page. Thin binding over
 * {@link ParentDashboardService}; all querying and entity-to-view flattening
 * lives in the application layer (ADR 0001).
 */
@Controller
@RequestMapping("/web/parent")
public class ParentPortalWebController {

    private final ParentDashboardService parentDashboardService;

    public ParentPortalWebController(ParentDashboardService parentDashboardService) {
        this.parentDashboardService = parentDashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication authentication) {
        ParentDashboardView view = parentDashboardService.dashboard(authentication).orElse(null);

        if (view == null) {
            model.addAttribute("errorMessage", "No parent data found. Please seed the database first.");
            model.addAttribute("activeQuests", List.of());
            model.addAttribute("awaitingQuests", List.of());
            model.addAttribute("awaitingRewards", List.of());
            model.addAttribute("announcements", List.of());
            model.addAttribute("students", List.of());
            model.addAttribute("studentMetrics", Map.of());
            model.addAttribute("pendingQuestCounts", Map.of());
            model.addAttribute("studentFees", Map.of());
            model.addAttribute("studentAwards", Map.of());
            return "parent_dashboard";
        }

        model.addAttribute("parent", view.parent());
        model.addAttribute("activeQuests", view.activeQuests());
        model.addAttribute("awaitingQuests", view.awaitingQuests());
        model.addAttribute("awaitingRewards", view.awaitingRewards());
        model.addAttribute("announcements", view.announcements());
        model.addAttribute("students", view.students());
        model.addAttribute("studentMetrics", view.studentMetrics());
        model.addAttribute("pendingQuestCounts", view.pendingQuestCounts());
        model.addAttribute("studentFees", view.studentFees());
        model.addAttribute("studentAwards", view.studentAwards());
        return "parent_dashboard";
    }
}
