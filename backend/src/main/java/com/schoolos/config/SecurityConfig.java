package com.schoolos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.schoolos.config.jwt.JwtAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    // ─── Chain 1: Mobile auth only — fully stateless, no session ────────────
    @Bean
    @Order(1)
    public SecurityFilterChain mobileAuthFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/mobile/auth/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // ─── Chain 2: All other /api/** — supports BOTH JWT and session auth ────
    // JWT filter runs first; if Bearer token present it authenticates via JWT.
    // If no Bearer token, Spring falls back to session-based authentication.
    // This allows the mobile app (JWT) and browser UI (session) to share routes.
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .securityContext(context -> context.securityContextRepository(
                        new org.springframework.security.web.context.DelegatingSecurityContextRepository(
                                new org.springframework.security.web.context.RequestAttributeSecurityContextRepository(),
                                new org.springframework.security.web.context.HttpSessionSecurityContextRepository()
                        )
                ))
                // NO .sessionManagement(STATELESS) — allows both JWT and session
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/onboard/**").permitAll() // public self-serve school signup
                        .requestMatchers("/api/principal/**").hasAnyRole("ADMIN", "PRINCIPAL") // read-only oversight
                        .requestMatchers("/api/teacher/timetable/seed").hasRole("ADMIN") // DEV ONLY seed - ADMIN only
                        .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/api/mobile/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/parent/**").hasRole("PARENT")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/curriculum/**").hasAnyRole("TEACHER", "ADMIN", "STUDENT")
                        .requestMatchers("/api/messages/**").hasAnyRole("TEACHER", "PARENT", "ADMIN")
                        .requestMatchers("/api/notifications/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ─── Chain 3: Web UI — session-based, form login, catch-all ─────────────
    @Bean
    @Order(3)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                                        com.schoolos.user.UserRepository userRepository,
                                                        com.schoolos.tenant.TenantRepository tenantRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/web/onboard/signup").permitAll() // public "create your school" form
                        .requestMatchers("/web/onboard/setup", "/web/onboard/complete").hasAnyRole("ADMIN", "PRINCIPAL")
                        .requestMatchers("/web/admin/dashboard").hasAnyRole("ADMIN", "TEACHER", "PRINCIPAL")
                        .requestMatchers("/web/admin/audit-log", "/web/admin/audit-log/**").hasAnyRole("ADMIN", "PRINCIPAL")
                        .requestMatchers("/web/admin/**").hasRole("ADMIN")
                        .requestMatchers("/web/teacher/dashboard").hasAnyRole("TEACHER", "ADMIN", "PRINCIPAL")
                        .requestMatchers("/web/teacher/**").hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/web/student/**").hasAnyRole("STUDENT", "PARENT", "ADMIN")
                        .requestMatchers("/web/parent/**").hasAnyRole("PARENT", "ADMIN")
                        .requestMatchers("/feed").authenticated() // announcement feed leaked cross-tenant data while public
                        .requestMatchers("/login", "/logout").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            String redirectUrl = "/";
                            var authorities = authentication.getAuthorities();
                            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                                    || authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_PRINCIPAL"))) {
                                redirectUrl = "/web/admin/dashboard";
                                boolean onboarded = userRepository.findByEmail(authentication.getName())
                                        .map(u -> tenantRepository.findById(u.getTenantId())
                                                .map(com.schoolos.tenant.Tenant::getEffectiveOnboardingCompleted)
                                                .orElse(true))
                                        .orElse(true);
                                if (!onboarded) {
                                    redirectUrl = "/web/onboard/setup";
                                }
                            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"))) {
                                redirectUrl = "/web/teacher/dashboard";
                            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_PARENT"))) {
                                redirectUrl = "/web/parent/portal";
                            } else if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))) {
                                redirectUrl = "/web/student/portal";
                            }
                            response.sendRedirect(redirectUrl);
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout"))
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/login", "/logout", "/test/**",
                                "/web/admin/student/add", "/web/admin/rewards/create", "/web/admin/post",
                                "/web/admin/class-sections/add", "/web/admin/school-classes/add", "/web/admin/staff/add", "/web/admin/parent/add",
                                "/web/admin/fees/invoice/create", "/web/admin/fees/collect",
                                "/web/admin/bus-routes/add", "/web/admin/bus-routes/*/assign-driver", "/web/admin/class-sections/*/assign-bus-route",
                                "/web/student/**", "/web/parent/**", "/web/teacher/**",
                                "/web/management/upload/process",
                                "/api/parent/**"
                        )
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:8081",
                "http://127.0.0.1:8081",
                "http://10.0.2.2:8081",
                "http://10.0.2.2:8080"
        ));
        // Wildcard pattern for the mobile-app web export deployed as a
        // separate Render static site — a distinct origin from the backend,
        // so it needs its own CORS allowance rather than the dev-only list
        // above. Origin patterns (not exact origins) are required for
        // wildcards when allowCredentials is true.
        configuration.setAllowedOriginPatterns(Arrays.asList("https://*.onrender.com"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}