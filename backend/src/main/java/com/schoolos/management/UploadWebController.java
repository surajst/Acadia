package com.schoolos.management;

import com.schoolos.common.AuditLogService;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/web/management/upload")
public class UploadWebController {

    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.schoolos.user.CurrentUserService currentUserService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public UploadWebController(ClassSectionRepository classSectionRepository,
                               StudentRepository studentRepository,
                               ParentRepository parentRepository) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
    }

    @GetMapping
    public String showUploadPage(HttpSession session) {
        if (session.getAttribute("currentUser") == null) {
            return "redirect:/web/login";
        }
        return "upload";
    }

    @PostMapping("/process")
    public String processUpload(@RequestParam("file") MultipartFile file,
                                HttpSession session,
                                Authentication authentication,
                                Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/web/login";

        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a valid CSV file.");
            return "upload";
        }

        List<Map<String, String>> rowResults = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            int rowNumber = 1; // header is row 1

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }
                rowNumber++;
                if (line.isBlank()) continue;

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 7) {
                    failed++;
                    rowResults.add(rowOutcome(rowNumber, "—", "Error", "Expected 7 columns, found " + cols.size()));
                    continue;
                }

                String firstName = cols.get(0).trim();
                String lastName = cols.get(1).trim();
                String rollNumber = cols.get(2).trim();
                String grade = cols.get(3).trim();
                String section = cols.get(4).trim();
                String parentName = cols.get(5).trim();
                String parentPhone = cols.get(6).trim();
                String label = (firstName + " " + lastName).trim();
                if (label.isEmpty()) label = "(row " + rowNumber + ")";

                try {
                    if (firstName.isEmpty() || lastName.isEmpty()) {
                        throw new IllegalArgumentException("First and last name are required");
                    }
                    if (parentPhone.isEmpty() || !parentPhone.matches("^\\+?[0-9\\s\\-()]{7,}$")) {
                        throw new IllegalArgumentException("Invalid phone number format for parent");
                    }
                    if (grade.isEmpty() || section.isEmpty()) {
                        throw new IllegalArgumentException("Grade and section are required");
                    }

                    // Duplicate roll number check — scoped to this tenant only.
                    if (!rollNumber.isEmpty()
                            && studentRepository.findByTenantIdAndRollNumber(currentUser.getTenantId(), rollNumber).isPresent()) {
                        skipped++;
                        rowResults.add(rowOutcome(rowNumber, label, "Skipped", "Roll number " + rollNumber + " already exists"));
                        continue;
                    }

                    // Class section — tenant-scoped lookup, auto-created if missing.
                    ClassSection classSection = classSectionRepository
                            .findByTenantIdAndGradeNameAndSectionName(currentUser.getTenantId(), grade, section)
                            .orElseGet(() -> {
                                ClassSection newSection = new ClassSection();
                                newSection.setId(UUID.randomUUID());
                                newSection.setTenantId(currentUser.getTenantId());
                                newSection.setAcademicYearId(currentUser.getAcademicYearId());
                                newSection.setGradeName(grade);
                                newSection.setSectionName(section);
                                return classSectionRepository.save(newSection);
                            });

                    // Parent — reuse an existing one for this tenant with the same phone number
                    // instead of creating a duplicate on every row/upload.
                    Parent parent = parentRepository
                            .findByTenantIdAndPhoneNumber(currentUser.getTenantId(), parentPhone)
                            .orElseGet(() -> {
                                String pFirstName = parentName.contains(" ") ? parentName.substring(0, parentName.indexOf(" ")) : parentName;
                                String pLastName = parentName.contains(" ") ? parentName.substring(parentName.indexOf(" ") + 1) : "";
                                Parent p = new Parent();
                                p.setId(UUID.randomUUID());
                                p.setTenantId(currentUser.getTenantId());
                                p.setAcademicYearId(currentUser.getAcademicYearId());
                                p.setFirstName(pFirstName.isEmpty() ? "Parent" : pFirstName);
                                p.setLastName(pLastName);
                                p.setPhoneNumber(parentPhone);
                                return parentRepository.save(p);
                            });

                    Student student = new Student();
                    student.setId(UUID.randomUUID());
                    student.setTenantId(currentUser.getTenantId());
                    student.setAcademicYearId(currentUser.getAcademicYearId());
                    student.setFirstName(firstName);
                    student.setLastName(lastName);
                    student.setRollNumber(rollNumber);
                    student.setClassSection(classSection);
                    student.getParents().add(parent);
                    studentRepository.save(student);

                    created++;
                    rowResults.add(rowOutcome(rowNumber, label, "Created", ""));
                } catch (Exception rowError) {
                    failed++;
                    rowResults.add(rowOutcome(rowNumber, label, "Error", rowError.getMessage()));
                }
            }
        } catch (Exception e) {
            model.addAttribute("error", "Could not read the uploaded file: " + e.getMessage());
            return "upload";
        }

        auditLogService.log(authentication, "ROSTER_BULK_IMPORT", "Student", null,
                "Bulk import: " + created + " created, " + skipped + " skipped, " + failed + " failed");

        model.addAttribute("success", created + " student" + (created == 1 ? "" : "s") + " imported"
                + (skipped > 0 ? ", " + skipped + " skipped as duplicates" : "")
                + (failed > 0 ? ", " + failed + " row(s) failed" : "") + ".");
        model.addAttribute("rowResults", rowResults);
        return "upload";
    }

    @PostMapping("/staff/process")
    public String processStaffUpload(@RequestParam("file") MultipartFile file,
                                     HttpSession session,
                                     Authentication authentication,
                                     Model model) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) return "redirect:/web/login";

        if (file.isEmpty()) {
            model.addAttribute("staffError", "Please select a valid CSV file.");
            return "upload";
        }

        UUID tenantId = currentUserService.getCurrentTenantId(authentication).orElse(currentUser.getTenantId());
        UUID academicYearId = currentUserService.getCurrentAcademicYearId(authentication).orElse(currentUser.getAcademicYearId());

        List<Map<String, String>> rowResults = new ArrayList<>();
        int created = 0, skipped = 0, failed = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            int rowNumber = 1;

            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                rowNumber++;
                if (line.isBlank()) continue;

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 3) {
                    failed++;
                    rowResults.add(rowOutcome(rowNumber, "—", "Error", "Expected 3 columns (FullName, Email, Role), found " + cols.size()));
                    continue;
                }

                String fullName = cols.get(0).trim();
                String email = cols.get(1).trim();
                String roleText = cols.get(2).trim().toUpperCase();
                String label = fullName.isEmpty() ? "(row " + rowNumber + ")" : fullName;

                try {
                    if (fullName.isEmpty() || email.isEmpty()) {
                        throw new IllegalArgumentException("Full name and email are required");
                    }
                    UserRole role;
                    try {
                        role = UserRole.valueOf(roleText);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Invalid role '" + roleText + "' (use TEACHER, PRINCIPAL, ADMIN, or DRIVER)");
                    }
                    if (role != UserRole.ADMIN && role != UserRole.PRINCIPAL && role != UserRole.TEACHER && role != UserRole.DRIVER) {
                        throw new IllegalArgumentException("Role must be TEACHER, PRINCIPAL, ADMIN, or DRIVER");
                    }
                    if (userRepository.existsByEmail(email)) {
                        skipped++;
                        rowResults.add(rowOutcome(rowNumber, label, "Skipped", "Email already in use: " + email));
                        continue;
                    }

                    String tempPassword = generateTempPassword();
                    User staff = new User();
                    staff.setId(UUID.randomUUID());
                    staff.setTenantId(tenantId);
                    staff.setAcademicYearId(academicYearId);
                    staff.setEmail(email);
                    staff.setPasswordHash(passwordEncoder.encode(tempPassword));
                    staff.setFullName(fullName);
                    staff.setRole(role);
                    staff.setActive(true);
                    staff.setApprovalStatus(User.ApprovalStatus.PENDING);
                    userRepository.save(staff);

                    created++;
                    // Surface the generated temp password so the admin can relay it (no email yet).
                    rowResults.add(rowOutcome(rowNumber, label, "Created", role.name() + " · temp password: " + tempPassword));
                } catch (Exception rowError) {
                    failed++;
                    rowResults.add(rowOutcome(rowNumber, label, "Error", rowError.getMessage()));
                }
            }
        } catch (Exception e) {
            model.addAttribute("staffError", "Could not read the uploaded file: " + e.getMessage());
            return "upload";
        }

        auditLogService.log(authentication, "STAFF_BULK_IMPORT", "User", null,
                "Bulk staff import: " + created + " created, " + skipped + " skipped, " + failed + " failed");

        model.addAttribute("staffSuccess", created + " staff member" + (created == 1 ? "" : "s") + " invited"
                + (skipped > 0 ? ", " + skipped + " skipped as duplicates" : "")
                + (failed > 0 ? ", " + failed + " row(s) failed" : "") + ". All are pending approval before they can sign in.");
        model.addAttribute("staffRowResults", rowResults);
        return "upload";
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 10; i++) p.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return p.append("!9").toString();
    }

    private Map<String, String> rowOutcome(int rowNumber, String label, String status, String detail) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("rowNumber", String.valueOf(rowNumber));
        row.put("label", label);
        row.put("status", status);
        row.put("detail", detail);
        return row;
    }

    /** Minimal RFC4180-style CSV line parser — handles quoted fields with embedded commas/quotes. */
    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
        }
        result.add(field.toString());
        return result;
    }
}
