package com.concept.roster.app;

import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.common.AuditLogService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Parent;
import com.concept.shared.data.SchoolClass;
import com.concept.shared.data.Student;
import com.concept.roster.data.RosterClassSectionRepository;
import com.concept.roster.data.RosterParentRepository;
import com.concept.roster.data.RosterSchoolClassRepository;
import com.concept.roster.data.RosterStudentPurger;
import com.concept.roster.data.RosterStudentRepository;
import com.concept.roster.data.RosterUserRepository;
import com.concept.user.User;
import com.concept.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application layer for admin student/parent management: registration, edits,
 * removal, and login (re)provisioning. Owns all the decisions — tenant checks,
 * login provisioning, guardian dedup/linking, and the audit trail — so the web
 * layer only binds requests and shapes responses (ADR 0001).
 *
 * <p>Every id lookup goes through a {@code findByIdAndTenantId} on a
 * {@link com.concept.common.TenantScopedRepository}, so this service physically
 * cannot read or mutate another tenant's student, guardian, classroom, or
 * login — the recurring cross-tenant IDOR class is designed out here.
 */
@Service
public class StudentAdminService {

    private final RosterStudentRepository studentRepository;
    private final RosterParentRepository parentRepository;
    private final RosterUserRepository userRepository;
    private final RosterSchoolClassRepository schoolClassRepository;
    private final RosterClassSectionRepository classSectionRepository;
    private final RosterStudentPurger studentPurger;
    private final StudentMetricRepository studentMetricRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final com.concept.tenant.TenantRepository tenantRepository;

    public StudentAdminService(RosterStudentRepository studentRepository,
                               RosterParentRepository parentRepository,
                               RosterUserRepository userRepository,
                               RosterSchoolClassRepository schoolClassRepository,
                               RosterClassSectionRepository classSectionRepository,
                               RosterStudentPurger studentPurger,
                               StudentMetricRepository studentMetricRepository,
                               PasswordEncoder passwordEncoder,
                               AuditLogService auditLogService,
                               com.concept.tenant.TenantRepository tenantRepository) {
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentPurger = studentPurger;
        this.studentMetricRepository = studentMetricRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.tenantRepository = tenantRepository;
    }

    // ---------------------------------------------------------------- add

    /**
     * Register a student, auto-provisioning a login (username = roll number)
     * when no explicit login is given, and optionally capturing + linking a
     * guardian. Returns the one-time credentials to relay to the admin (student
     * and/or guardian), or {@code null} if none were generated.
     */
    @Transactional
    public String addStudent(String firstName, String lastName, String rollNumber, UUID schoolClassId,
                             String loginEmail, String loginPassword,
                             String guardianFirstName, String guardianLastName, String guardianPhone,
                             UUID tenantId, UUID academicYearId, Authentication authentication) {
        StringBuilder creds = new StringBuilder();

        // Provision a student login the same way bulk import does: username =
        // roll number with an auto-generated temp password, unless the caller
        // supplied a login or the roll number is already used as a username.
        String studentEmail = loginEmail;
        String studentPassword = loginPassword;
        if ((studentEmail == null || studentEmail.isBlank())
                && rollNumber != null && !rollNumber.isBlank()) {
            studentEmail = buildUsername(firstName, rollNumber, tenantId);
            studentPassword = studentEmail == null ? null : generateTempPassword();
        }

        Student student = createStudent(firstName, lastName, rollNumber, schoolClassId,
                studentEmail, studentPassword, tenantId, academicYearId);
        auditLogService.log(authentication, "STUDENT_ADDED", "Student", student.getId(),
                "Added student " + firstName + " " + lastName + " (roll " + rollNumber + ")");

        if (studentPassword != null && studentEmail != null) {
            creds.append("Student login — ").append(studentEmail).append(" / ").append(studentPassword);
        }

        if (guardianFirstName != null && !guardianFirstName.isBlank()) {
            String guardianEmail = null;
            String guardianPassword = null;
            if (guardianPhone != null && !guardianPhone.isBlank()) {
                guardianEmail = buildGuardianUsername(guardianFirstName, guardianPhone, tenantId);
                if (guardianEmail != null) {
                    guardianPassword = generateTempPassword();
                }
            }
            Parent guardian = addParentInternal(guardianFirstName.trim(),
                    guardianLastName != null ? guardianLastName.trim() : "",
                    guardianPhone, student.getId(), guardianEmail, guardianPassword, tenantId, academicYearId);
            auditLogService.log(authentication, "PARENT_ADDED", "Parent", guardian.getId(),
                    "Linked guardian " + guardianFirstName + " to student " + firstName + " " + lastName);
            if (guardianPassword != null) {
                if (creds.length() > 0) creds.append("  ·  ");
                creds.append("Guardian login — ").append(guardianEmail).append(" / ").append(guardianPassword);
            }
        }

        return creds.length() > 0 ? creds.toString() : null;
    }

    /**
     * Builds a student's sign-in username as firstname + roll number, qualified by
     * the school's subdomain: "asha6a-01@greenwood".
     *
     * <p>The subdomain is not decoration. User.email is globally unique across
     * every school, and the previous version used the bare roll number, so the
     * first school to register "6A-01" claimed that username for the whole
     * system. Every later school registering their own 6A-01 fell through the
     * existence check and silently got no login at all -- no error, no warning,
     * just a child who could not sign in. Qualifying by tenant removes the shared
     * namespace; the first name makes the username less guessable from a class
     * list than a bare roll number.
     *
     * @return the username, or null when one cannot be formed or stays taken
     */
    /** Student login: first name + roll number, qualified by the school. */
    private String buildUsername(String firstName, String rollNumber, UUID tenantId) {
        return qualifiedUsername(sanitiseForUsername(firstName) + sanitiseForUsername(rollNumber), tenantId);
    }

    /**
     * Guardian login: first name + phone number, qualified by the school.
     *
     * <p>The phone number used to be the whole username. User.email is unique
     * across the platform, so the first school to register a given number
     * claimed it globally and every later school silently got no guardian
     * login at all -- the caller checked existsByEmail and simply skipped
     * provisioning. Same defect as bare roll numbers, same fix: the school's
     * subdomain makes the namespace per-school.
     */
    private String buildGuardianUsername(String firstName, String phoneNumber, UUID tenantId) {
        return qualifiedUsername(sanitiseForUsername(firstName) + sanitiseForUsername(phoneNumber), tenantId);
    }

    /**
     * Turns a local part into a school-qualified username, or null when one
     * cannot be formed. Null means "no login", and every caller has to treat it
     * as such rather than falling back to an unqualified value.
     */
    private String qualifiedUsername(String localSeed, UUID tenantId) {
        String subdomain = tenantId == null ? null : tenantRepository.findById(tenantId)
                .map(com.concept.tenant.Tenant::getSubdomain).orElse(null);
        if (subdomain == null || subdomain.isBlank()) {
            return null;
        }
        String local = localSeed == null ? "" : localSeed;
        if (local.isBlank()) {
            return null;
        }
        String candidate = local + "@" + sanitiseForUsername(subdomain);
        if (!userRepository.existsByEmail(candidate)) {
            return candidate;
        }
        // A clash within one school should be near-impossible, but it must not
        // silently mean "no login" the way the global namespace did.
        for (int i = 2; i <= 20; i++) {
            String next = local + i + "@" + sanitiseForUsername(subdomain);
            if (!userRepository.existsByEmail(next)) {
                return next;
            }
        }
        return null;
    }

    /** Lowercase, keeping only characters a family can retype without ambiguity. */
    private String sanitiseForUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9.-]", "");
    }

    /**
     * Create a standalone parent, optionally provisioning a login and linking to
     * a student. Returns the new (or reused) guardian id. Throws
     * {@link IllegalArgumentException} if the requested login email is taken.
     */
    @Transactional
    public UUID addParent(String firstName, String lastName, String phoneNumber, UUID studentId,
                          String loginEmail, String loginPassword, UUID tenantId, UUID academicYearId,
                          Authentication authentication) {
        Parent parent = addParentInternal(firstName, lastName, phoneNumber, studentId,
                loginEmail, loginPassword, tenantId, academicYearId);
        auditLogService.log(authentication, "PARENT_ADDED", "Parent", parent.getId(),
                "Added parent " + firstName + " " + lastName);
        return parent.getId();
    }

    // ------------------------------------------------------------- update

    /**
     * Update a student's details, optionally moving classrooms and updating or
     * linking a guardian. Returns any one-time guardian credentials that were
     * generated. Throws {@link StudentProfileNotFoundException} when the student
     * is not in this tenant, or {@link IllegalArgumentException} on invalid input.
     */
    @Transactional
    public Optional<String> updateStudent(UUID id, UUID tenantId, UUID academicYearId,
                                          String firstName, String lastName, String rollNumber, UUID schoolClassId,
                                          String guardianFirstName, String guardianLastName, String guardianPhone,
                                          Authentication authentication) {
        Student student = studentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new StudentProfileNotFoundException(id));
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()
                || rollNumber == null || rollNumber.isBlank()) {
            throw new IllegalArgumentException("First name, last name and roll number are required.");
        }
        student.setFirstName(firstName.trim());
        student.setLastName(lastName.trim());
        student.setRollNumber(rollNumber.trim());
        // Optional class move: re-point the student to another classroom (tenant-scoped),
        // resolving the matching ClassSection so rosters/profile stay consistent.
        if (schoolClassId != null) {
            schoolClassRepository.findByIdAndTenantId(schoolClassId, tenantId).ifPresent(sc -> {
                student.setSchoolClass(sc);
                classSectionRepository
                        .findByTenantIdAndGradeNameAndSectionName(tenantId, sc.getGradeLevel(), sc.getSectionName())
                        .ifPresent(student::setClassSection);
            });
        }
        studentRepository.save(student);
        auditLogService.log(authentication, "STUDENT_UPDATED", "Student", student.getId(),
                "Updated student to " + firstName + " " + lastName + " (roll " + rollNumber + ")");

        String newCredentials = null;
        // Guardian: update the existing primary guardian in place, or create and
        // link a new one (provisioning a login) if the student had none.
        if (guardianFirstName != null && !guardianFirstName.isBlank()) {
            Parent existing = student.getParents() != null
                    ? student.getParents().stream().filter(p -> tenantId.equals(p.getTenantId())).findFirst().orElse(null)
                    : null;
            if (existing != null) {
                existing.setFirstName(guardianFirstName.trim());
                existing.setLastName(guardianLastName != null ? guardianLastName.trim() : "");
                if (guardianPhone != null) existing.setPhoneNumber(guardianPhone.trim());
                parentRepository.save(existing);
                auditLogService.log(authentication, "PARENT_UPDATED", "Parent", existing.getId(),
                        "Updated guardian for student " + firstName + " " + lastName);
            } else {
                String gEmail = null;
                String gPass = null;
                if (guardianPhone != null && !guardianPhone.isBlank()) {
                    gEmail = buildGuardianUsername(guardianFirstName, guardianPhone, tenantId);
                    if (gEmail != null) {
                        gPass = generateTempPassword();
                    }
                }
                Parent g = addParentInternal(guardianFirstName.trim(),
                        guardianLastName != null ? guardianLastName.trim() : "",
                        guardianPhone, student.getId(), gEmail, gPass, tenantId, academicYearId);
                auditLogService.log(authentication, "PARENT_ADDED", "Parent", g.getId(),
                        "Linked guardian " + guardianFirstName + " to student " + firstName + " " + lastName);
                if (gPass != null) {
                    newCredentials = "Guardian login — " + gEmail + " / " + gPass;
                }
            }
        }
        return Optional.ofNullable(newCredentials);
    }

    // ------------------------------------------------------------- remove

    /** Remove a student (tenant-scoped) and cascade-delete their dependent rows. */
    @Transactional
    public void removeStudent(UUID id, UUID tenantId, Authentication authentication) {
        Student student = studentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new StudentProfileNotFoundException(id));
        String studentName = student.getFirstName() + " " + student.getLastName();
        studentPurger.purge(id);
        auditLogService.log(authentication, "STUDENT_REMOVED", "Student", id, "Removed student " + studentName);
    }

    // -------------------------------------------------------- reset logins

    /**
     * Reset (or first-time create) the student's login, returning the one-time
     * credentials. Throws {@link StudentProfileNotFoundException} if the student
     * is not in this tenant, or {@link IllegalArgumentException} if no login can
     * be provisioned (missing/duplicate roll number).
     */
    @Transactional
    public String resetStudentLogin(UUID id, UUID tenantId, Authentication authentication) {
        Student student = studentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new StudentProfileNotFoundException(id));
        String newPassword = generateTempPassword();
        User u = student.getUserId() != null
                ? userRepository.findByIdAndTenantId(student.getUserId(), tenantId).orElse(null) : null;
        if (u == null) {
            // No login existed yet, so one is created here. This used to key it
            // on the bare roll number, which meant an admin recovering a
            // locked-out student hit "roll number already in use" because a
            // DIFFERENT school had that roll -- failing on the one path that
            // exists to fix the problem.
            String username = buildUsername(student.getFirstName(), student.getRollNumber(), tenantId);
            if (username == null) {
                throw new IllegalArgumentException(
                        "Cannot create a student login: a roll number and first name are required.");
            }
            u = new User();
            u.setId(UUID.randomUUID());
            u.setTenantId(tenantId);
            u.setAcademicYearId(student.getAcademicYearId());
            u.setEmail(username);
            u.setFullName(student.getFirstName() + " " + student.getLastName());
            u.setRole(UserRole.STUDENT);
            u.setActive(true);
            u.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(u);
            student.setUserId(u.getId());
            studentRepository.save(student);
        } else {
            u.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(u);
        }
        auditLogService.log(authentication, "STUDENT_LOGIN_RESET", "User", u.getId(),
                "Reset student login for " + student.getFirstName() + " " + student.getLastName());
        return "Student login — " + u.getEmail() + " / " + newPassword;
    }

    /**
     * Reset (or first-time create) a guardian's login, returning the one-time
     * credentials. Throws {@link StudentProfileNotFoundException} if the guardian
     * is not in this tenant, or {@link IllegalArgumentException} if no login can
     * be provisioned (no username, or username already in use).
     */
    @Transactional
    public String resetParentLogin(UUID parentId, UUID tenantId, Authentication authentication) {
        Parent parent = parentRepository.findByIdAndTenantId(parentId, tenantId)
                .orElseThrow(() -> new StudentProfileNotFoundException(parentId));
        String newPassword = generateTempPassword();
        User u = parent.getUserId() != null
                ? userRepository.findByIdAndTenantId(parent.getUserId(), tenantId).orElse(null) : null;
        if (u == null) {
            // Same story as students: keyed on the raw phone number, this threw
            // when another school had already registered that number.
            String username = buildGuardianUsername(
                    parent.getFirstName(), parent.getPhoneNumber(), tenantId);
            if (username == null) {
                throw new IllegalArgumentException(
                        "Cannot create a guardian login: a phone number and first name are required.");
            }
            u = new User();
            u.setId(UUID.randomUUID());
            u.setTenantId(tenantId);
            u.setAcademicYearId(parent.getAcademicYearId());
            u.setEmail(username);
            u.setFullName(parent.getFirstName() + " " + parent.getLastName());
            u.setRole(UserRole.PARENT);
            u.setActive(true);
            u.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(u);
            parent.setUserId(u.getId());
            parent.setEmail(username);
            parentRepository.save(parent);
        } else {
            u.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(u);
        }
        auditLogService.log(authentication, "PARENT_LOGIN_RESET", "User", u.getId(),
                "Reset guardian login for " + parent.getFirstName() + " " + parent.getLastName());
        return "Guardian login — " + u.getEmail() + " / " + newPassword;
    }

    // -------------------------------------------------------- internals

    /** Core student creation + zeroed metric seed (tenant-scoped classroom resolve). */
    private Student createStudent(String firstName, String lastName, String rollNumber, UUID schoolClassId,
                                  String loginEmail, String loginPassword, UUID tenantId, UUID academicYearId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndTenantId(schoolClassId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Classroom not found: " + schoolClassId));

        ClassSection classSection = classSectionRepository
                .findByTenantIdAndGradeNameAndSectionName(tenantId, schoolClass.getGradeLevel(), schoolClass.getSectionName())
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

        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setTenantId(tenantId);
        student.setAcademicYearId(academicYearId);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setRollNumber(rollNumber);
        student.setSchoolClass(schoolClass);
        student.setClassSection(classSection);

        if (loginEmail != null && !loginEmail.isBlank() && loginPassword != null && !loginPassword.isBlank()) {
            if (userRepository.existsByEmail(loginEmail)) {
                throw new IllegalArgumentException("Email already in use: " + loginEmail);
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

        StudentMetric metric = new StudentMetric();
        metric.setId(UUID.randomUUID());
        metric.setTenantId(tenantId);
        metric.setAcademicYearId(academicYearId);
        metric.setStudent(student);
        metric.setSchoolXp(0);
        metric.setParentXp(0);
        metric.setActiveStreak(0);
        studentMetricRepository.save(metric);

        return student;
    }

    /**
     * Create or reuse a guardian and link to a student. Multi-child households:
     * an existing guardian on the same phone in this tenant is reused (and linked
     * to the additional child) instead of duplicated, so one parent = one login =
     * one child-switcher.
     */
    private Parent addParentInternal(String firstName, String lastName, String phoneNumber, UUID studentId,
                                     String loginEmail, String loginPassword, UUID tenantId, UUID academicYearId) {
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            Parent existing = parentRepository.findAllByTenantIdAndPhoneNumber(tenantId, phoneNumber)
                    .stream().findFirst().orElse(null);
            if (existing != null) {
                if (existing.getUserId() == null
                        && loginEmail != null && !loginEmail.isBlank()
                        && loginPassword != null && !loginPassword.isBlank()) {
                    provisionParentLogin(existing, loginEmail, loginPassword, tenantId, academicYearId);
                    parentRepository.save(existing);
                }
                linkParentToStudent(existing, studentId, tenantId);
                return existing;
            }
        }

        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setAcademicYearId(academicYearId);
        parent.setFirstName(firstName);
        parent.setLastName(lastName);
        parent.setPhoneNumber(phoneNumber);

        if (loginEmail != null && !loginEmail.isBlank() && loginPassword != null && !loginPassword.isBlank()) {
            provisionParentLogin(parent, loginEmail, loginPassword, tenantId, academicYearId);
        }

        parentRepository.save(parent);
        linkParentToStudent(parent, studentId, tenantId);
        return parent;
    }

    /** Create a PARENT login for the given parent (username = loginEmail). */
    private void provisionParentLogin(Parent parent, String loginEmail, String loginPassword,
                                      UUID tenantId, UUID academicYearId) {
        if (userRepository.existsByEmail(loginEmail)) {
            throw new IllegalArgumentException("Email already in use: " + loginEmail);
        }
        User parentUser = new User();
        parentUser.setId(UUID.randomUUID());
        parentUser.setTenantId(tenantId);
        parentUser.setAcademicYearId(academicYearId);
        parentUser.setEmail(loginEmail);
        parentUser.setPasswordHash(passwordEncoder.encode(loginPassword));
        parentUser.setFullName(parent.getFirstName() + " " + parent.getLastName());
        parentUser.setRole(UserRole.PARENT);
        parentUser.setActive(true);
        userRepository.save(parentUser);
        parent.setUserId(parentUser.getId());
        parent.setEmail(loginEmail);
    }

    /** Link a parent to a student (tenant-scoped; no-op if the student is missing); dedups via the Set. */
    private void linkParentToStudent(Parent parent, UUID studentId, UUID tenantId) {
        if (studentId == null) return;
        studentRepository.findByIdAndTenantId(studentId, tenantId).ifPresent(student -> {
            student.getParents().add(parent);
            studentRepository.save(student);
        });
    }

    /** Human-relayable temp password (no ambiguous chars), mirrors RosterImportService. */
    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < 10; i++) p.append(chars.charAt(rnd.nextInt(chars.length())));
        return p.append("!9").toString();
    }
}
