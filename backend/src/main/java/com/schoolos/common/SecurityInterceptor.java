package com.schoolos.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class SecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !("anonymousUser".equals(auth.getName()))) {
            HttpSession session = request.getSession(true);
            if (session.getAttribute("currentUser") == null) {
                String username = auth.getName();
                
                try {
                    com.schoolos.user.UserRepository userRepo = org.springframework.web.context.support.WebApplicationContextUtils
                        .getRequiredWebApplicationContext(request.getServletContext())
                        .getBean(com.schoolos.user.UserRepository.class);
                        
                    java.util.Optional<com.schoolos.user.User> userOpt = userRepo.findByEmail(username);
                    if (!userOpt.isPresent()) {
                        userOpt = userRepo.findByEmail(username + "@greenwood.com");
                    }
                    
                    if (userOpt.isPresent()) {
                        session.setAttribute("currentUser", userOpt.get());
                    } else {
                        com.schoolos.user.User mockUser = new com.schoolos.user.User();
                        mockUser.setId(java.util.UUID.fromString("22222222-3333-4444-5555-666666666666"));
                        mockUser.setTenantId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"));
                        mockUser.setAcademicYearId(java.util.UUID.fromString("00000000-0000-0000-0000-111111111111"));
                        mockUser.setEmail(username + "@greenwood.com");
                        mockUser.setPasswordHash("hash");
                        mockUser.setFullName(username.substring(0, 1).toUpperCase() + username.substring(1) + " User");
                        
                        com.schoolos.user.UserRole userRole = com.schoolos.user.UserRole.TEACHER;
                        for (GrantedAuthority authority : auth.getAuthorities()) {
                            String roleName = authority.getAuthority();
                            if (roleName.equals("ROLE_ADMIN")) {
                                userRole = com.schoolos.user.UserRole.ADMIN;
                            } else if (roleName.equals("ROLE_PARENT")) {
                                userRole = com.schoolos.user.UserRole.PARENT;
                            }
                        }
                        mockUser.setRole(userRole);
                        mockUser.setActive(true);
                        
                        session.setAttribute("currentUser", mockUser);
                    }
                } catch (Exception e) {
                    System.err.println("Error initializing currentUser in session: " + e.getMessage());
                }
            }
        }
        
        if (path.startsWith("/web/feed") || path.startsWith("/web/admin") || path.startsWith("/web/management")) {
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("currentUser") == null) {
                response.sendRedirect("/web/login");
                return false;
            }
        }
        
        return true;
    }
}

