package com.schoolos.tenant;

import com.schoolos.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;

@Controller
public class AdminOnboardingWebController {

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private TenantRepository tenantRepository;

    @GetMapping("/web/onboard/signup")
    public String getSignupPage() {
        return "onboard_signup";
    }

    @GetMapping("/web/onboard/setup")
    public String getSetupWizard() {
        return "onboard_setup";
    }

    /** Marks the current admin's tenant as onboarded so future logins skip the wizard. */
    @GetMapping("/web/onboard/complete")
    public String completeOnboarding(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (tenantId != null) {
            tenantRepository.findById(tenantId).ifPresent(tenant -> {
                tenant.setOnboardingCompleted(true);
                tenantRepository.save(tenant);
            });
        }
        return "redirect:/web/admin/dashboard";
    }
}
