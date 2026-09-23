package com.concept.rewards.web;

import com.concept.rewards.app.RewardsService;
import com.concept.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Interface layer for admin reward management. Binds the request, resolves the
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
                               @RequestParam("inventoryCount") int inventoryCount,
                               Authentication authentication) {
        try {
            rewardsService.createReward(title, description, xpCost, displayEmoji, inventoryCount,
                    tenantContext.getTenantId().orElse(null),
                    tenantContext.getAcademicYearId().orElse(null), authentication);
        } catch (IllegalArgumentException e) {
            // Carried back to the page rather than swallowed: the form's own
            // min="1" is advisory, and a rejected reward that redirects to a
            // silent success looks exactly like an accepted one.
            return "redirect:/web/admin/management?rewardError=" + encode(e.getMessage());
        }
        return "redirect:/web/admin/management?success=reward_created";
    }

    @PostMapping("/web/admin/rewards/{id}/update")
    @ResponseBody
    public Object updateReward(@PathVariable("id") UUID id,
                               @RequestParam("title") String title,
                               @RequestParam("description") String description,
                               @RequestParam("xpCost") int xpCost,
                               @RequestParam("displayEmoji") String displayEmoji,
                               @RequestParam("inventoryCount") int inventoryCount,
                               Authentication authentication) {
        try {
            rewardsService.updateReward(id, title, description, xpCost, displayEmoji, inventoryCount,
                    tenantContext.getTenantId().orElse(null), authentication);
            return Map.of("status", "updated");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/web/admin/rewards/{id}/delete")
    @ResponseBody
    public Object deleteReward(@PathVariable("id") UUID id, Authentication authentication) {
        try {
            rewardsService.deleteReward(id, tenantContext.getTenantId().orElse(null), authentication);
            return Map.of("status", "deleted");
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private static String encode(String message) {
        return URLEncoder.encode(message == null ? "Could not save the reward." : message,
                StandardCharsets.UTF_8);
    }
}
