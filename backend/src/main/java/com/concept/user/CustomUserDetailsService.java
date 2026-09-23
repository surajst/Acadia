package com.concept.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CustomUserDetailsService.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String rawEmail) throws UsernameNotFoundException {
        // Both sign-in paths land here (the form login and the JWT filter), so
        // this is the one place the typed address has to be canonicalised.
        String email = User.normaliseEmail(rawEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Both refusals below are deliberately UsernameNotFoundException, which
        // the DaoAuthenticationProvider reports to the browser as "Invalid
        // username or password". Throwing DisabledException instead would say
        // out loud which addresses have accounts, so the reason is logged for
        // whoever is supporting the user rather than returned to whoever is
        // trying the door.
        if (!user.isActive()) {
            log.warn("Sign-in refused for {}: the account is deactivated", email);
            throw new UsernameNotFoundException("User account is disabled: " + email);
        }

        if (user.getApprovalStatus() == User.ApprovalStatus.PENDING
                || user.getApprovalStatus() == User.ApprovalStatus.REJECTED) {
            log.warn("Sign-in refused for {}: approval status is {}", email, user.getApprovalStatus());
            throw new UsernameNotFoundException("Account is awaiting PRINCIPAL/ADMIN approval: " + email);
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }
}
