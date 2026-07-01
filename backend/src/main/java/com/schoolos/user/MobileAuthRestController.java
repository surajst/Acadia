package com.schoolos.user;

import com.schoolos.config.jwt.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthRestController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    public MobileAuthRestController(UserRepository userRepository, 
                                    PasswordEncoder passwordEncoder, 
                                    JwtUtils jwtUtils, 
                                    UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.email);
            
            if (passwordEncoder.matches(loginRequest.password, userDetails.getPassword())) {
                String jwt = jwtUtils.generateToken(userDetails);
                
                String role = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .findFirst()
                    .orElse("USER");

                // Retrieve full user for profile details
                User user = userRepository.findByEmail(loginRequest.email).orElse(null);
                String fullName = user != null ? user.getFullName() : loginRequest.email;
                String[] nameParts = fullName.split(" ", 2);
                String fName = nameParts[0];
                String lName = nameParts.length > 1 ? nameParts[1] : "";

                Map<String, Object> response = new HashMap<>();
                response.put("token", jwt);
                response.put("userId", user != null ? user.getId() : java.util.UUID.randomUUID());
                response.put("role", role);
                response.put("firstName", fName);
                response.put("lastName", lName);
                response.put("email", loginRequest.email);

                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            // User not found or other auth error
        }

        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
}
