package com.schoolos.tenant;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminOnboardingWebController {

    @GetMapping("/web/onboard/signup")
    public String getSignupPage() {
        return "onboard_signup";
    }

    @GetMapping("/web/onboard/setup")
    public String getSetupWizard() {
        return "onboard_setup";
    }
}
