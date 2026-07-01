package com.schoolos.user;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping({"/", "/login", "/web/login"})
    public String showLoginForm() {
        return "login";
    }

    @PostMapping("/web/auth/login")
    public String loginUser(@RequestParam String email, 
                            @RequestParam String password, 
                            HttpSession session) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                session.setAttribute("currentUser", user);
                return "redirect:/web/feed";
            }
        }
        
        return "redirect:/web/login?error=true";
    }

    @GetMapping("/web/auth/logout")
    public String logoutUser(HttpSession session) {
        session.invalidate();
        return "redirect:/web/login";
    }
}
