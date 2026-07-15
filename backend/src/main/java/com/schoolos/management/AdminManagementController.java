package com.schoolos.management;

import com.schoolos.common.AuditLogService;
import com.schoolos.transport.BusRoute;
import com.schoolos.transport.BusRouteRepository;
import com.schoolos.user.CurrentUserService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ParentRepository parentRepository;

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

    @GetMapping("/web/admin/class-sections")
    @ResponseBody
    public List<ClassSection> listClassSections(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        return tenantId != null ? classSectionRepository.findByTenantId(tenantId) : Collections.emptyList();
    }

    @PostMapping("/web/admin/class-sections/add")
    public String addClassSection(@RequestParam("gradeName") String gradeName,
                                   @RequestParam("sectionName") String sectionName,
                                   @RequestParam(value = "roomNumber", required = false) String roomNumber,
                                   Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

        ClassSection classSection = new ClassSection();
        classSection.setId(UUID.randomUUID());
        classSection.setTenantId(tenantId);
        classSection.setAcademicYearId(academicYearId);
        classSection.setGradeName(gradeName);
        classSection.setSectionName(sectionName);
        classSection.setRoomNumber(roomNumber);
        classSectionRepository.save(classSection);
        auditLogService.log(authentication, "CLASS_SECTION_ADDED", "ClassSection", classSection.getId(),
                "Added class section " + gradeName + " - " + sectionName);

        return "redirect:/web/admin/management?success=class_section_added";
    }

    @PostMapping("/web/admin/school-classes/add")
    public String addSchoolClass(@RequestParam("gradeLevel") String gradeLevel,
                                  @RequestParam("sectionName") String sectionName,
                                  @RequestParam(value = "roomNumber", required = false) String roomNumber,
                                  @RequestParam("totalCapacity") Integer totalCapacity,
                                  Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(UUID.randomUUID());
        schoolClass.setTenantId(tenantId);
        schoolClass.setAcademicYearId(academicYearId);
        schoolClass.setGradeLevel(gradeLevel);
        schoolClass.setSectionName(sectionName);
        schoolClass.setRoomNumber(roomNumber);
        schoolClass.setTotalCapacity(totalCapacity);
        schoolClassRepository.save(schoolClass);
        auditLogService.log(authentication, "SCHOOL_CLASS_ADDED", "SchoolClass", schoolClass.getId(),
                "Added classroom " + gradeLevel + " - " + sectionName);

        return "redirect:/web/admin/management?success=school_class_added";
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
        staff.setApprovalStatus(com.schoolos.user.User.ApprovalStatus.PENDING);
        userRepository.save(staff);
        auditLogService.log(authentication, "STAFF_INVITED", "User", staff.getId(),
                "Invited " + role.name() + " " + fullName + " (" + email + ") — awaiting PRINCIPAL/ADMIN approval");

        return java.util.Map.of("status", "created", "id", staff.getId(), "approvalStatus", "PENDING");
    }

    @PostMapping("/web/admin/student/add")
    @Transactional
    public String addStudent(@RequestParam("firstName") String firstName,
                             @RequestParam("lastName") String lastName,
                             @RequestParam("rollNumber") String rollNumber,
                             @RequestParam("schoolClassId") UUID schoolClassId,
                             @RequestParam(value = "loginEmail", required = false) String loginEmail,
                             @RequestParam(value = "loginPassword", required = false) String loginPassword,
                             Authentication authentication) {
        // Enforce role checks so only ADMIN roles can register students
        if (authentication != null) {
            boolean isAdmin = false;
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if ("ROLE_ADMIN".equals(auth.getAuthority())) {
                    isAdmin = true;
                    break;
                }
            }
            if (!isAdmin) {
                throw new RuntimeException("Access denied: Only administrators can register students");
            }
        }

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

        SchoolClass schoolClass = schoolClassRepository.findById(schoolClassId)
            .orElseThrow(() -> new RuntimeException("SchoolClass not found: " + schoolClassId));

        ClassSection classSection = classSectionRepository.findByTenantIdAndGradeNameAndSectionName(tenantId, schoolClass.getGradeLevel(), schoolClass.getSectionName())
            .orElseGet(() -> {
                List<ClassSection> sections = tenantId != null ? classSectionRepository.findByTenantId(tenantId) : List.of();
                if (!sections.isEmpty()) {
                    return sections.get(0);
                }
                ClassSection cs = new ClassSection();
                cs.setId(UUID.randomUUID());
                cs.setTenantId(tenantId);
                cs.setAcademicYearId(academicYearId);
                cs.setGradeName(schoolClass.getGradeLevel());
                cs.setSectionName(schoolClass.getSectionName());
                cs.setRoomNumber(schoolClass.getRoomNumber());
                return classSectionRepository.save(cs);
            });

        UUID studentId = UUID.randomUUID();
        Student student = new Student();
        student.setId(studentId);
        student.setTenantId(tenantId);
        student.setAcademicYearId(academicYearId);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setRollNumber(rollNumber);
        student.setSchoolClass(schoolClass);
        student.setClassSection(classSection);

        // Optionally provision a real login for this student, so they aren't
        // stuck with a null userId (and thus unable to log in at all) —
        // previously this endpoint never set userId.
        if (loginEmail != null && !loginEmail.isBlank() && loginPassword != null && !loginPassword.isBlank()) {
            if (userRepository.existsByEmail(loginEmail)) {
                throw new RuntimeException("Email already in use: " + loginEmail);
            }
            User studentUser = new User();
            studentUser.setId(UUID.randomUUID());
            studentUser.setTenantId(tenantId);
            studentUser.setAcademicYearId(academicYearId);
            studentUser.setEmail(loginEmail);
            studentUser.setPasswordHash(passwordEncoder.encode(loginPassword));
            studentUser.setFullName(firstName + " " + lastName);
            studentUser.setRole(UserRole.STUDENT);
            studentUser.setActive(true);
            userRepository.save(studentUser);
            student.setUserId(studentUser.getId());
        }

        studentRepository.save(student);

        // Automatically instantiate and save a default StudentMetric record
        StudentMetric metric = new StudentMetric();
        metric.setId(UUID.randomUUID());
        metric.setTenantId(tenantId);
        metric.setAcademicYearId(academicYearId);
        metric.setStudent(student);
        metric.setSchoolXp(0);
        metric.setParentXp(0);
        metric.setActiveStreak(0);
        studentMetricRepository.save(metric);
        auditLogService.log(authentication, "STUDENT_ADDED", "Student", student.getId(),
                "Added student " + firstName + " " + lastName + " (roll " + rollNumber + ")");

        return "redirect:/web/admin/management?success=student_added";
    }

    @PostMapping("/web/admin/parent/add")
    @Transactional
    @ResponseBody
    public Object addParent(@RequestParam("firstName") String firstName,
                             @RequestParam("lastName") String lastName,
                             @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
                             @RequestParam(value = "studentId", required = false) UUID studentId,
                             @RequestParam(value = "loginEmail", required = false) String loginEmail,
                             @RequestParam(value = "loginPassword", required = false) String loginPassword,
                             Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(null);

        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setAcademicYearId(academicYearId);
        parent.setFirstName(firstName);
        parent.setLastName(lastName);
        parent.setPhoneNumber(phoneNumber);

        if (loginEmail != null && !loginEmail.isBlank() && loginPassword != null && !loginPassword.isBlank()) {
            if (userRepository.existsByEmail(loginEmail)) {
                return java.util.Map.of("error", "Email already in use: " + loginEmail);
            }
            User parentUser = new User();
            parentUser.setId(UUID.randomUUID());
            parentUser.setTenantId(tenantId);
            parentUser.setAcademicYearId(academicYearId);
            parentUser.setEmail(loginEmail);
            parentUser.setPasswordHash(passwordEncoder.encode(loginPassword));
            parentUser.setFullName(firstName + " " + lastName);
            parentUser.setRole(UserRole.PARENT);
            parentUser.setActive(true);
            userRepository.save(parentUser);
            parent.setUserId(parentUser.getId());
            parent.setEmail(loginEmail);
        }

        parentRepository.save(parent);

        if (studentId != null) {
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student != null) {
                student.getParents().add(parent);
                studentRepository.save(student);
            }
        }

        auditLogService.log(authentication, "PARENT_ADDED", "Parent", parent.getId(),
                "Added parent " + firstName + " " + lastName);

        return java.util.Map.of("status", "created", "id", parent.getId());
    }

    @PostMapping("/web/admin/student/{id}/remove")
    @Transactional
    public String removeStudent(@PathVariable("id") UUID id, Authentication authentication) {
        // Enforce role checks so only ADMIN roles can remove students
        if (authentication != null) {
            boolean isAdmin = false;
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                if ("ROLE_ADMIN".equals(auth.getAuthority())) {
                    isAdmin = true;
                    break;
                }
            }
            if (!isAdmin) {
                throw new RuntimeException("Access denied: Only administrators can remove students");
            }
        }

        String studentName = studentRepository.findById(id)
                .map(s -> s.getFirstName() + " " + s.getLastName())
                .orElse(id.toString());

        try {
            // Clean up dependencies recursively before deleting student
            jdbcTemplate.update("DELETE FROM fee_transactions WHERE invoice_id IN (SELECT id FROM fee_invoices WHERE student_id = ?)", id);
            jdbcTemplate.update("DELETE FROM fee_invoices WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM student_metrics WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM academic_submissions WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM attendance WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM parent_quests WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM parent_rewards WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM student_parents WHERE student_id = ?", id);
            jdbcTemplate.update("DELETE FROM students WHERE id = ?", id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove student: " + e.getMessage(), e);
        }

        auditLogService.log(authentication, "STUDENT_REMOVED", "Student", id, "Removed student " + studentName);

        return "redirect:/web/admin/management?success=student_removed";
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

