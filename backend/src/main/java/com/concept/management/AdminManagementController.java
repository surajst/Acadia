package com.concept.management;

import com.concept.common.AuditLogService;
import com.concept.transport.BusRoute;
import com.concept.transport.BusRouteRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
public class AdminManagementController {

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RewardItemRepository rewardItemRepository;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private BusRouteRepository busRouteRepository;

    @GetMapping("/web/admin/management")
    public String showAdminManagement(Model model, Authentication authentication) {
        String role = "ADMIN";
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if (authority.startsWith("ROLE_")) {
                    role = authority.substring(5);
                }
            }
        }
        model.addAttribute("currentUserRole", role);

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        List<SchoolClass> classList = Collections.emptyList();
        try {
            classList = tenantId != null ? schoolClassRepository.findByTenantId(tenantId) : Collections.emptyList();
        } catch (Exception e) {
            // gracefully catch
        }

        List<RewardItem> rewardInventoryList = Collections.emptyList();
        try {
            rewardInventoryList = tenantId != null ? rewardItemRepository.findByTenantId(tenantId) : Collections.emptyList();
        } catch (Exception e) {
            // gracefully catch
        }

        long totalStudents = 0;
        long totalStaff = 0;
        long totalClassrooms = 0;
        try {
            totalStudents = tenantId != null ? studentRepository.findByTenantId(tenantId).size() : 0;
            totalStaff = userRepository.countByRoleAndTenantId(UserRole.ADMIN, tenantId)
                    + userRepository.countByRoleAndTenantId(UserRole.PRINCIPAL, tenantId)
                    + userRepository.countByRoleAndTenantId(UserRole.TEACHER, tenantId)
                    + userRepository.countByRoleAndTenantId(UserRole.DRIVER, tenantId);
            totalClassrooms = tenantId != null ? schoolClassRepository.countByTenantId(tenantId) : 0;
        } catch (Exception e) {
            // gracefully catch
        }

        model.addAttribute("classList", classList);
        model.addAttribute("rewardInventoryList", rewardInventoryList);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("totalStaff", totalStaff);
        model.addAttribute("totalClassrooms", totalClassrooms);

        model.addAttribute("systemScope", "ADMIN_CONSOLE");

        return "admin_management";
    }

    @PostMapping("/web/admin/rewards/create")
    public String createReward(@RequestParam("title") String title,
                               @RequestParam("description") String description,
                               @RequestParam("xpCost") int xpCost,
                               @RequestParam("displayEmoji") String displayEmoji,
                               @RequestParam("inventoryCount") int inventoryCount,
                               Authentication authentication) {
        try {
            UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
            UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

            RewardItem reward = new RewardItem(
                UUID.randomUUID(),
                title,
                description,
                xpCost,
                displayEmoji,
                inventoryCount
            );
            reward.setTenantId(tenantId);
            reward.setAcademicYearId(academicYearId);

            rewardItemRepository.save(reward);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create reward: " + e.getMessage(), e);
        }

        return "redirect:/web/admin/management";
    }


    @GetMapping("/web/admin/staff")
    @ResponseBody
    public List<java.util.Map<String, Object>> listStaff(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (tenantId == null) return Collections.emptyList();
        return userRepository.findByTenantIdAndRoleIn(tenantId, Arrays.asList(UserRole.ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER, UserRole.DRIVER))
                .stream()
                .map(u -> java.util.Map.<String, Object>of(
                        "id", u.getId(),
                        "fullName", u.getFullName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name(),
                        "active", u.isActive()
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/web/admin/staff/add")
    @ResponseBody
    public Object addStaff(@RequestParam("fullName") String fullName,
                            @RequestParam("email") String email,
                            @RequestParam("password") String password,
                            @RequestParam("role") UserRole role,
                            Authentication authentication) {
        if (role != UserRole.ADMIN && role != UserRole.PRINCIPAL && role != UserRole.TEACHER && role != UserRole.DRIVER) {
            return java.util.Map.of("error", "Staff role must be ADMIN, PRINCIPAL, TEACHER, or DRIVER");
        }
        if (userRepository.existsByEmail(email)) {
            return java.util.Map.of("error", "Email already in use: " + email);
        }

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setTenantId(tenantId);
        staff.setAcademicYearId(academicYearId);
        staff.setEmail(email);
        staff.setPasswordHash(passwordEncoder.encode(password));
        staff.setFullName(fullName);
        staff.setRole(role);
        staff.setActive(true);
        staff.setApprovalStatus(com.concept.user.User.ApprovalStatus.PENDING);
        userRepository.save(staff);
        auditLogService.log(authentication, "STAFF_INVITED", "User", staff.getId(),
                "Invited " + role.name() + " " + fullName + " (" + email + ") — awaiting PRINCIPAL/ADMIN approval");

        return java.util.Map.of("status", "created", "id", staff.getId(), "approvalStatus", "PENDING");
    }

    @GetMapping("/web/admin/bus-routes")
    @ResponseBody
    public List<BusRoute> listBusRoutes(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return tenantId != null ? busRouteRepository.findByTenantId(tenantId) : Collections.emptyList();
    }

    @PostMapping("/web/admin/bus-routes/add")
    @ResponseBody
    public Object addBusRoute(@RequestParam("name") String name, Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

        BusRoute route = new BusRoute();
        route.setId(UUID.randomUUID());
        route.setTenantId(tenantId);
        route.setAcademicYearId(academicYearId);
        route.setName(name);
        busRouteRepository.save(route);
        auditLogService.log(authentication, "BUS_ROUTE_ADDED", "BusRoute", route.getId(), "Added bus route " + name);

        return java.util.Map.of("status", "created", "id", route.getId());
    }

    @PostMapping("/web/admin/bus-routes/{id}/assign-driver")
    @ResponseBody
    public Object assignBusRouteDriver(@PathVariable("id") UUID id,
                                        @RequestParam("driverId") UUID driverId,
                                        Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        BusRoute route = busRouteRepository.findById(id).orElse(null);
        if (route == null || tenantId == null || !tenantId.equals(route.getTenantId())) {
            return java.util.Map.of("error", "Bus route not found");
        }

        User driver = userRepository.findById(driverId).orElse(null);
        if (driver == null || driver.getRole() != UserRole.DRIVER || !tenantId.equals(driver.getTenantId())) {
            return java.util.Map.of("error", "Driver not found");
        }

        route.setDriverId(driverId);
        busRouteRepository.save(route);
        auditLogService.log(authentication, "BUS_ROUTE_DRIVER_ASSIGNED", "BusRoute", route.getId(),
                "Assigned driver " + driver.getFullName() + " to route " + route.getName());

        return java.util.Map.of("status", "assigned");
    }

    @PostMapping("/web/admin/class-sections/{id}/assign-bus-route")
    @ResponseBody
    public Object assignClassSectionBusRoute(@PathVariable("id") UUID id,
                                              @RequestParam("busRouteId") UUID busRouteId,
                                              Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);

        ClassSection section = classSectionRepository.findById(id).orElse(null);
        if (section == null || tenantId == null || !tenantId.equals(section.getTenantId())) {
            return java.util.Map.of("error", "Class section not found");
        }

        BusRoute route = busRouteRepository.findById(busRouteId).orElse(null);
        if (route == null || !tenantId.equals(route.getTenantId())) {
            return java.util.Map.of("error", "Bus route not found");
        }

        section.setBusRouteId(busRouteId);
        classSectionRepository.save(section);
        auditLogService.log(authentication, "CLASS_SECTION_BUS_ROUTE_ASSIGNED", "ClassSection", section.getId(),
                "Assigned bus route " + route.getName() + " to " + section.getGradeName() + " - " + section.getSectionName());

        return java.util.Map.of("status", "assigned");
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "ADMIN");
        return "admin_management";
    }
}
