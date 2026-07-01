package com.schoolos.management;

import com.schoolos.user.CurrentUserService;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
            totalStaff = userRepository.countByRole(UserRole.ADMIN) + userRepository.countByRole(UserRole.TEACHER);
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

    @PostMapping("/web/admin/student/add")
    @Transactional
    public String addStudent(@RequestParam("firstName") String firstName,
                             @RequestParam("lastName") String lastName,
                             @RequestParam("rollNumber") String rollNumber,
                             @RequestParam("schoolClassId") UUID schoolClassId,
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

        return "redirect:/web/admin/management?success=student_added";
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

        return "redirect:/web/admin/management?success=student_removed";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "ADMIN");
        return "admin_management";
    }
}

