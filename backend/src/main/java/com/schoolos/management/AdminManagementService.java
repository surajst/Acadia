package com.schoolos.management;

import com.schoolos.academics.StudentMetric;
import com.schoolos.academics.StudentMetricRepository;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persistence + login-provisioning logic for the admin add-student and
 * add-parent flows, pulled out of AdminManagementController so the controller
 * keeps only role checks, audit logging, and response shaping. A duplicate
 * login email throws {@link IllegalArgumentException}; callers translate that
 * into their own error response.
 */
@Service
public class AdminManagementService {

    private final SchoolClassRepository schoolClassRepository;
    private final ClassSectionRepository classSectionRepository;
    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final ParentRepository parentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminManagementService(SchoolClassRepository schoolClassRepository,
                                  ClassSectionRepository classSectionRepository,
                                  StudentRepository studentRepository,
                                  StudentMetricRepository studentMetricRepository,
                                  ParentRepository parentRepository,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder) {
        this.schoolClassRepository = schoolClassRepository;
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.parentRepository = parentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Create a student in the given school class, optionally provisioning a login,
     * and seed a zeroed StudentMetric. Throws {@link IllegalArgumentException} if
     * the requested login email is already in use.
     */
    @Transactional
    public Student addStudent(String firstName, String lastName, String rollNumber, UUID schoolClassId,
                              String loginEmail, String loginPassword, UUID tenantId, UUID academicYearId) {
        SchoolClass schoolClass = schoolClassRepository.findById(schoolClassId)
                .orElseThrow(() -> new RuntimeException("SchoolClass not found: " + schoolClassId));

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

        // Optionally provision a real login so the student isn't left with a null
        // userId (and thus unable to sign in at all).
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
     * Create a parent, optionally provisioning a login and linking to a student.
     * Throws {@link IllegalArgumentException} if the login email is already in use.
     */
    @Transactional
    public Parent addParent(String firstName, String lastName, String phoneNumber, UUID studentId,
                            String loginEmail, String loginPassword, UUID tenantId, UUID academicYearId) {
        Parent parent = new Parent();
        parent.setId(UUID.randomUUID());
        parent.setTenantId(tenantId);
        parent.setAcademicYearId(academicYearId);
        parent.setFirstName(firstName);
        parent.setLastName(lastName);
        parent.setPhoneNumber(phoneNumber);

        if (loginEmail != null && !loginEmail.isBlank() && loginPassword != null && !loginPassword.isBlank()) {
            if (userRepository.existsByEmail(loginEmail)) {
                throw new IllegalArgumentException("Email already in use: " + loginEmail);
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

        return parent;
    }
}
