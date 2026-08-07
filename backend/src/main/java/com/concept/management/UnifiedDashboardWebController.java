package com.concept.management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.common.NotificationDeliveryService;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Controller
public class UnifiedDashboardWebController {

    @Autowired 
    private ClassSectionRepository classSectionRepo;

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AcademicSubmissionRepository academicSubmissionRepository;

    @Autowired
    private StudentMetricRepository studentMetricRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private StudentProgressRepository studentProgressRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private AdminProgressService adminProgressService;

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private FeeManagementService feeManagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectAssignmentRepository subjectAssignmentRepository;

    @Autowired
    private TeacherDashboardService teacherDashboardService;

    /** Redirect bridge: /web/management/attendance → canonical teacher attendance route */
    @GetMapping("/web/management/attendance")
    public String managementAttendanceRedirect() {
        return "redirect:/web/teacher/attendance";
    }

    @GetMapping("/web/admin/dashboard")
    public String showUnifiedDashboard(
            @RequestParam(value = "classId", required = false) UUID classId,
            @RequestParam(value = "name", required = false) String nameFilter,
            @RequestParam(value = "gradeLevel", required = false) String gradeLevelFilter,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model, Authentication authentication) {
        String role = "TEACHER";
        if (authentication != null) {
            for (GrantedAuthority auth : authentication.getAuthorities()) {
                String authority = auth.getAuthority();
                if (authority.startsWith("ROLE_")) {
                    role = authority.substring(5);
                }
            }
        }
        model.addAttribute("currentUserRole", role);
        model.addAttribute("userRoleString", role);

        // Setup IDs dynamically
        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(null);
        String username = authentication != null ? authentication.getName() : "teacher_1";
        UUID teacherId = UUID.nameUUIDFromBytes(username.getBytes());
        String activeTeacherName = username;

        // Every section visible to this tenant — used as an ADMIN-view
        // fallback below. Never unscoped: falling back to "the first section
        // found anywhere" would leak another tenant's roster onto this page.
        List<ClassSection> checkSections = Collections.emptyList();
        try {
            checkSections = tenantId != null ? classSectionRepo.findByTenantId(tenantId) : Collections.emptyList();
        } catch (Exception e) {
            // gracefully catch
        }

        model.addAttribute("userDisplayName", activeTeacherName);

        // Fetch classrooms based on the matched active test context parameters
        List<ClassSection> assignedClassrooms = Collections.emptyList();
        try {
            assignedClassrooms = classSectionRepo.findByTeacherIdAndTenantId(teacherId, tenantId);
        } catch (Exception e) {
            // gracefully catch
        }
        
        // If query returns empty because of a missing teacher ID linkage, grab all sections as fallback to display
        if (assignedClassrooms.isEmpty() && !checkSections.isEmpty()) {
            assignedClassrooms = checkSections;
        }

        // Normalise empty-string filter params to null so JPQL IS NULL checks work
        String effectiveName = (nameFilter != null && !nameFilter.isBlank()) ? nameFilter.trim() : null;
        String effectiveGrade = (gradeLevelFilter != null && !gradeLevelFilter.isBlank()) ? gradeLevelFilter.trim() : null;

        Pageable pageable = PageRequest.of(page, size);
        List<Student> conditionalRoster = Collections.emptyList();
        long totalRosterItems = 0;
        int totalRosterPages = 0;
        try {
            if (classId != null && (effectiveName != null || effectiveGrade != null)) {
                // Class-specific view with name/grade filters: no dedicated paginated
                // query exists for this combination, so filter in-memory (bounded by
                // one class section's roster size, not the whole tenant).
                List<Student> byClass = studentRepository.findBySchoolClassId(classId);
                String nameNeedle = effectiveName == null ? null : effectiveName.toLowerCase();
                conditionalRoster = byClass.stream()
                    .filter(s -> nameNeedle == null ||
                                 s.getFirstName().toLowerCase().contains(nameNeedle) ||
                                 s.getLastName().toLowerCase().contains(nameNeedle) ||
                                 // Match the full "First Last" too, so a search for the
                                 // whole displayed name (which spans both columns) hits.
                                 (s.getFirstName() + " " + s.getLastName()).toLowerCase().contains(nameNeedle))
                    .filter(s -> effectiveGrade == null ||
                                 (s.getClassSection() != null &&
                                  effectiveGrade.equals(s.getClassSection().getGradeName())))
                    .collect(Collectors.toList());
                totalRosterItems = conditionalRoster.size();
                totalRosterPages = 1;
            } else if (classId != null) {
                Page<Student> classPage = studentRepository.findBySchoolClassId(classId, pageable);
                conditionalRoster = classPage.getContent();
                totalRosterItems = classPage.getTotalElements();
                totalRosterPages = classPage.getTotalPages();
            } else if (effectiveName != null || effectiveGrade != null) {
                // Filtered search across all sections visible to this user
                Page<Student> searchPage;
                if (!assignedClassrooms.isEmpty()) {
                    searchPage = studentRepository.findByClassSectionInAndNameAndGrade(
                        assignedClassrooms, effectiveName, effectiveGrade, pageable);
                } else {
                    searchPage = studentRepository.findByNameContainingAndGrade(tenantId, effectiveName, effectiveGrade, pageable);
                }
                conditionalRoster = searchPage.getContent();
                totalRosterItems = searchPage.getTotalElements();
                totalRosterPages = searchPage.getTotalPages();
            } else if (!assignedClassrooms.isEmpty()) {
                Page<Student> rosterPage = studentService.findByClassSectionIn(assignedClassrooms, pageable);
                conditionalRoster = rosterPage.getContent();
                totalRosterItems = rosterPage.getTotalElements();
                totalRosterPages = rosterPage.getTotalPages();
            }
        } catch (Exception e) {
            // gracefully catch
        }

        // Collect distinct grade names from this tenant's sections for the filter dropdown
        List<String> allGradeNames = checkSections.stream()
            .map(ClassSection::getGradeName)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        long totalStudents = 0;
        long activeAbsences = 0;
        try {
            totalStudents = tenantId != null ? studentRepository.countByTenantId(tenantId) : 0;
            activeAbsences = tenantId != null
                    ? attendanceRepository.countByTenantIdAndAttendanceDateAndStatus(tenantId, LocalDate.now(), AttendanceStatus.ABSENT)
                    : 0;
        } catch (Exception e) {
            // gracefully catch
        }
        int attendancePercentage = totalStudents == 0 ? 0 : (int) Math.round(((double)(totalStudents - activeAbsences) / totalStudents) * 100);

        // PRINCIPAL: read-only school-wide rollups instead of classroom-roster
        // data — oversight without the ADMIN/TEACHER data-entry surfaces.
        if ("PRINCIPAL".equals(role)) {
            try {
                model.addAttribute("schoolProgress", adminProgressService.getSchoolWideProgress(tenantId));
            } catch (Exception e) {
                model.addAttribute("schoolProgress", Collections.emptyMap());
            }
            try {
                model.addAttribute("feeSummary", feeManagementService.getSchoolWideFeeSummary(tenantId));
            } catch (Exception e) {
                model.addAttribute("feeSummary", Collections.emptyMap());
            }
        }

        model.addAttribute("availableClassesMenu", assignedClassrooms);
        model.addAttribute("studentDisplayRoster", conditionalRoster);
        model.addAttribute("students", conditionalRoster);
        model.addAttribute("allGradeNames", allGradeNames);
        model.addAttribute("filterName", nameFilter != null ? nameFilter : "");
        model.addAttribute("filterGrade", gradeLevelFilter != null ? gradeLevelFilter : "");
        model.addAttribute("systemScope", "RESTRICTED_VIEW");
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("activeAbsences", activeAbsences);
        model.addAttribute("attendancePercentage", attendancePercentage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalRosterPages);
        model.addAttribute("totalRosterItems", totalRosterItems);
        model.addAttribute("pageSize", size);

        return "unified_dashboard";
    }


    @GetMapping("/web/teacher/dashboard")
    public String viewTeacherDashboard(Model model, Authentication authentication) {
        String role = "TEACHER";
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
        String email = authentication != null ? authentication.getName() : null;
        TeacherDashboardService.VerificationQueues queues =
                teacherDashboardService.buildVerificationQueues(email, role, tenantId);

        model.addAttribute("pendingSubmissions", queues.pendingSubmissions());
        model.addAttribute("pendingProgressQueue", queues.pendingProgress());
        return "teacher_dashboard";
    }


    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        ex.printStackTrace();
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("currentUserRole", "TEACHER");
        return "unified_dashboard";
    }

}
