package com.schoolos.management;

import com.schoolos.common.AuditLogService;
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

        List<SchoolClass> classList = Collections.emptyList();
        try {
            classList = schoolClassRepository.findAll();
        } catch (Exception e) {
            // gracefully catch
        }

        List<RewardItem> rewardInventoryList = Collections.emptyList();
        try {
            rewardInventoryList = rewardItemRepository.findAll();
        } catch (Exception e) {
            // gracefully catch
        }
        
        long totalStudents = 0;
        long totalStaff = 0;
        long totalClassrooms = 0;
        try {
            totalStudents = studentRepository.count();
            totalStaff = userRepository.countByRole(UserRole.ADMIN) + userRepository.countByRole(UserRole.PRINCIPAL) + userRepository.countByRole(UserRole.TEACHER);
            totalClassrooms = schoolClassRepository.count();
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

    @GetMapping("/web/admin/staff")
    @ResponseBody
    public List<java.util.Map<String, Object>> listStaff(Authentication authentication) {
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        if (tenantId == null) return Collections.emptyList();
        return userRepository.findByTenantIdAndRoleIn(tenantId, Arrays.asList(UserRole.ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER))
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
        if (role != UserRole.ADMIN && role != UserRole.PRINCIPAL && role != UserRole.TEACHER) {
            return java.util.Map.of("error", "Staff role must be ADMIN, PRINCIPAL, or TEACHER");
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
        userRepository.save(staff);
        auditLogService.log(authentication, "STAFF_INVITED", "User", staff.getId(),
                "Invited " + role.name() + " " + fullName + " (" + email + ")");

        return java.util.Map.of("status", "created", "id", staff.getId());
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

        ClassSection classSection = classSectionRepository.findByGradeNameAndSectionName(schoolClass.getGradeLevel(), schoolClass.getSectionName())
            .orElseGet(() -> {
                List<ClassSection> sections = classSectionRepository.findAll();
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

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "ADMIN");
        return "admin_management";
    }
}

