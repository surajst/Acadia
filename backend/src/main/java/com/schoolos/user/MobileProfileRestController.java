package com.schoolos.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/user")
public class MobileProfileRestController {

    private final UserRepository userRepository;

    public MobileProfileRestController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> {
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("email", user.getEmail());
                    profile.put("fullName", user.getFullName());
                    profile.put("role", user.getRole().name());
                    
                    // Simple split for first/last name
                    String[] nameParts = user.getFullName().split(" ", 2);
                    profile.put("firstName", nameParts[0]);
                    profile.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
                    
                    return ResponseEntity.ok(profile);
                })
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }
}
