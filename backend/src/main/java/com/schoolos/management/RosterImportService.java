package com.schoolos.management;

import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the bulk-import business logic (parsing, validation, de-duplication,
 * entity + login creation) for both the student roster and the staff CSV.
 * Controllers keep only the HTTP/session/model/audit concerns and delegate the
 * actual work here, so this logic is unit-testable without an HTTP round trip
 * and the preview and commit paths share one implementation.
 *
 * <p>Row outcomes are returned as {@code Map<String,String>} with the keys
 * {@code rowNumber}, {@code label}, {@code status}, {@code detail} — the shape
 * the Thymeleaf {@code upload.html} template already iterates over.
 */
@Service
public class RosterImportService {

    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom RANDOM = new SecureRandom();

    public RosterImportService(ClassSectionRepository classSectionRepository,
                               StudentRepository studentRepository,
                               ParentRepository parentRepository,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Result of the student dry-run preview: the parsed rows (to stash and later commit), per-row outcomes, and counts. */
    public record StudentPreview(List<List<String>> rows,
                                 List<Map<String, String>> outcomes,
                                 int willCreate, int willSkip, int willFail) {
        public boolean canCommit() { return willCreate > 0; }
        public String summary() { return willCreate + " to create, " + willSkip + " to skip, " + willFail + " with errors."; }
    }

    /** Result of a committed import (students or staff): per-row outcomes and counts. */
    public record ImportResult(List<Map<String, String>> outcomes, int created, int skipped, int failed) {}

    // ---------------------------------------------------------------------
    // Student roster
    // ---------------------------------------------------------------------

    /**
     * Parse and validate a student roster file as a dry run — classifies each
     * row as Create / Skip / Error (including intra-file duplicate roll numbers)
     * without writing anything. Returns the parsed rows so the caller can stash
     * them and commit later via {@link #commitStudents}.
     */
    public StudentPreview previewStudents(MultipartFile file, User currentUser) throws IOException {
        List<List<String>> allRows = parseRows(file);
        List<Map<String, String>> outcomes = new ArrayList<>();
        int willCreate = 0, willSkip = 0, willFail = 0;
        // Roll numbers seen earlier in THIS file, so an intra-file duplicate is
        // flagged as Skip in the preview rather than silently colliding on commit.
        Set<String> seenRolls = new HashSet<>();

        for (int i = 1; i < allRows.size(); i++) { // row 0 is the header
            int rowNumber = i + 1;
            List<String> cols = allRows.get(i);
            if (cols.stream().allMatch(c -> c == null || c.isBlank())) continue;

            if (cols.size() < 7) {
                willFail++;
                outcomes.add(rowOutcome(rowNumber, "—", "Error", "Expected 7 columns, found " + cols.size()));
                continue;
            }

            String firstName = cols.get(0).trim();
            String lastName = cols.get(1).trim();
            String rollNumber = cols.get(2).trim();
            String grade = cols.get(3).trim();
            String section = cols.get(4).trim();
            String parentPhone = cols.get(6).trim();
            String label = (firstName + " " + lastName).trim();
            if (label.isEmpty()) label = "(row " + rowNumber + ")";

            if (firstName.isEmpty() || lastName.isEmpty()) {
                willFail++;
                outcomes.add(rowOutcome(rowNumber, label, "Error", "First and last name are required"));
            } else if (parentPhone.isEmpty() || !parentPhone.matches("^\\+?[0-9\\s\\-()]{7,}$")) {
                willFail++;
                outcomes.add(rowOutcome(rowNumber, label, "Error", "Invalid phone number format for parent"));
            } else if (grade.isEmpty() || section.isEmpty()) {
                willFail++;
                outcomes.add(rowOutcome(rowNumber, label, "Error", "Grade and section are required"));
            } else if (!rollNumber.isEmpty() && !seenRolls.add(rollNumber)) {
                willSkip++;
                outcomes.add(rowOutcome(rowNumber, label, "Skip", "Duplicate roll number " + rollNumber + " earlier in this file"));
            } else if (!rollNumber.isEmpty()
                    && studentRepository.findByTenantIdAndRollNumber(currentUser.getTenantId(), rollNumber).isPresent()) {
                willSkip++;
                outcomes.add(rowOutcome(rowNumber, label, "Skip", "Roll number " + rollNumber + " already exists"));
            } else {
                willCreate++;
                outcomes.add(rowOutcome(rowNumber, label, "Create", "Grade " + grade + " · Section " + section));
            }
        }

        return new StudentPreview(allRows, outcomes, willCreate, willSkip, willFail);
    }

    /**
     * Commit previously-parsed student rows: create students, parents, class
     * sections, and logins. Re-validates each row (defence in depth — the
     * preview is advisory only).
     */
    public ImportResult commitStudents(List<List<String>> allRows, User currentUser) {
        List<Map<String, String>> outcomes = new ArrayList<>();
        int created = 0, skipped = 0, failed = 0;

        for (int i = 1; i < allRows.size(); i++) { // row 0 is the header
            int rowNumber = i + 1;
            List<String> cols = allRows.get(i);
            if (cols.stream().allMatch(c -> c == null || c.isBlank())) continue;

            if (cols.size() < 7) {
                failed++;
                outcomes.add(rowOutcome(rowNumber, "—", "Error", "Expected 7 columns, found " + cols.size()));
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
                    outcomes.add(rowOutcome(rowNumber, label, "Skipped", "Roll number " + rollNumber + " already exists"));
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

                // Provision logins so imported students/parents can actually sign in.
                // Student logs in with their roll number, parent with their phone —
                // both already in the import, each with an auto-generated temp password.
                StringBuilder creds = new StringBuilder();

                if (!rollNumber.isEmpty() && !userRepository.existsByEmail(rollNumber)) {
                    String studentPassword = generateTempPassword();
                    User studentUser = new User();
                    studentUser.setId(UUID.randomUUID());
                    studentUser.setTenantId(currentUser.getTenantId());
                    studentUser.setAcademicYearId(currentUser.getAcademicYearId());
                    studentUser.setEmail(rollNumber);
                    studentUser.setPasswordHash(passwordEncoder.encode(studentPassword));
                    studentUser.setFullName(firstName + " " + lastName);
                    studentUser.setRole(UserRole.STUDENT);
                    studentUser.setActive(true);
                    userRepository.save(studentUser);
                    student.setUserId(studentUser.getId());
                    creds.append("student login ").append(rollNumber).append(" / ").append(studentPassword);
                }

                // Only create a parent login if this parent doesn't already have one
                // (a reused parent from a prior row/upload keeps their existing login).
                if (parent.getUserId() == null && !parentPhone.isEmpty() && !userRepository.existsByEmail(parentPhone)) {
                    String parentPassword = generateTempPassword();
                    User parentUser = new User();
                    parentUser.setId(UUID.randomUUID());
                    parentUser.setTenantId(currentUser.getTenantId());
                    parentUser.setAcademicYearId(currentUser.getAcademicYearId());
                    parentUser.setEmail(parentPhone);
                    parentUser.setPasswordHash(passwordEncoder.encode(parentPassword));
                    parentUser.setFullName(parent.getFirstName() + " " + parent.getLastName());
                    parentUser.setRole(UserRole.PARENT);
                    parentUser.setActive(true);
                    userRepository.save(parentUser);
                    parent.setUserId(parentUser.getId());
                    parent.setEmail(parentPhone);
                    parentRepository.save(parent);
                    if (creds.length() > 0) creds.append(" · ");
                    creds.append("parent login ").append(parentPhone).append(" / ").append(parentPassword);
                }

                studentRepository.save(student);

                created++;
                outcomes.add(rowOutcome(rowNumber, label, "Created", creds.toString()));
            } catch (Exception rowError) {
                failed++;
                outcomes.add(rowOutcome(rowNumber, label, "Error", rowError.getMessage()));
            }
        }

        return new ImportResult(outcomes, created, skipped, failed);
    }

    // ---------------------------------------------------------------------
    // Staff
    // ---------------------------------------------------------------------

    /**
     * Parse a staff CSV and create staff logins directly (no preview step).
     * Each new staff member gets an auto-generated temp password (surfaced in
     * the row detail to relay) and stays PENDING approval before they can sign in.
     */
    public ImportResult importStaff(MultipartFile file, UUID tenantId, UUID academicYearId) throws IOException {
        List<List<String>> allRows = parseRows(file);
        List<Map<String, String>> outcomes = new ArrayList<>();
        int created = 0, skipped = 0, failed = 0;

        for (int i = 1; i < allRows.size(); i++) { // row 0 is the header
            int rowNumber = i + 1;
            List<String> cols = allRows.get(i);
            if (cols.stream().allMatch(c -> c == null || c.isBlank())) continue;

            if (cols.size() < 3) {
                failed++;
                outcomes.add(rowOutcome(rowNumber, "—", "Error", "Expected 3 columns (FullName, Email, Role), found " + cols.size()));
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
                    outcomes.add(rowOutcome(rowNumber, label, "Skipped", "Email already in use: " + email));
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
                outcomes.add(rowOutcome(rowNumber, label, "Created", role.name() + " · temp password: " + tempPassword));
            } catch (Exception rowError) {
                failed++;
                outcomes.add(rowOutcome(rowNumber, label, "Error", rowError.getMessage()));
            }
        }

        return new ImportResult(outcomes, created, skipped, failed);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

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

    /**
     * Reads every row of an uploaded CSV or .xlsx into a list of string
     * columns (row 0 is the header). Format is chosen by filename extension;
     * Excel cells are read as their displayed text so numeric roll numbers /
     * phone numbers come through as written.
     */
    private static List<List<String>> parseRows(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        boolean excel = name != null && (name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"));
        List<List<String>> rows = new ArrayList<>();

        if (excel) {
            try (org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
                org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
                org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
                int lastCol = 0;
                for (org.apache.poi.ss.usermodel.Row r : sheet) lastCol = Math.max(lastCol, r.getLastCellNum());
                for (org.apache.poi.ss.usermodel.Row r : sheet) {
                    List<String> cols = new ArrayList<>();
                    for (int c = 0; c < lastCol; c++) {
                        org.apache.poi.ss.usermodel.Cell cell = r.getCell(c,
                                org.apache.poi.ss.usermodel.Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cols.add(cell == null ? "" : fmt.formatCellValue(cell));
                    }
                    rows.add(cols);
                }
            }
        } else {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    rows.add(parseCsvLine(line));
                }
            }
        }
        return rows;
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
