package com.concept.rewards.web;

import com.concept.rewards.app.RewardsService;
import com.concept.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Interface layer for admin reward creation. Binds the request, resolves the
 * tenant, and delegates — no persistence, no entities (ADR 0001).
 */
@Controller
public class RewardsController {

    private final RewardsService rewardsService;
    private final TenantContext tenantContext;

    public RewardsController(RewardsService rewardsService, TenantContext tenantContext) {
        this.rewardsService = rewardsService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/web/admin/rewards/create")
    public String createReward(@RequestParam("title") String title,
                               @RequestParam("description") String description,
                               @RequestParam("xpCost") int xpCost,
                               @RequestParam("displayEmoji") String displayEmoji,
                               @RequestParam("inventoryCount") int inventoryCount) {
        rewardsService.createReward(title, description, xpCost, displayEmoji, inventoryCount,
                tenantContext.getTenantId().orElse(null), tenantContext.getAcademicYearId().orElse(null));
        return "redirect:/web/admin/management";
    }
}
