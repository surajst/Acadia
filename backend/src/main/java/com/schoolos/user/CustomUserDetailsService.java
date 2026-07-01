package com.schoolos.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println(">>> CustomUserDetailsService.loadUserByUsername called for email: " + email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.out.println(">>> User NOT FOUND: " + email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        System.out.println(">>> User found! email=" + user.getEmail() + " active=" + user.isActive());
        System.out.println(">>> Password hash in DB: " + user.getPasswordHash());

        if (!user.isActive()) {
            throw new UsernameNotFoundException("User account is disabled: " + email);
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }
}
