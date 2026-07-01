package com.schoolos;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = "PilotLaunchSecure2026!";
        String hash = "$2a$10$OORnz8ZDBXFVAgDCcKkwUun3OB3jLLOpVuey/JDC1NxFKD8ki80jK";
        System.out.println("Matches: " + encoder.matches(raw, hash));
        System.out.println("Matches 'GreenwoodStaffTesting2026!': " + encoder.matches("GreenwoodStaffTesting2026!", hash));
    }
}
