package com.schoolos.tenant;

import com.schoolos.academics.SubjectService;
import com.schoolos.common.AuditLogService;
import com.schoolos.management.ClassSection;
import com.schoolos.management.ClassSectionRepository;
import com.schoolos.user.User;
import com.schoolos.user.UserRepository;
import com.schoolos.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.UUID;

@Service
public class TenantOnboardingService {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ClassSectionRepository classSectionRepository;

    public static class DuplicateSubdomainException extends RuntimeException {
        public DuplicateSubdomainException(String subdomain) {
            super("Subdomain already in use: " + subdomain);
        }
    }

    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException(String email) {
            super("Email already in use: " + email);
        }
    }

    public static class NewSchool {
        public final Tenant tenant;
        public final AcademicYear academicYear;
        public final User adminUser;

        public NewSchool(Tenant tenant, AcademicYear academicYear, User adminUser) {
            this.tenant = tenant;
            this.academicYear = academicYear;
            this.adminUser = adminUser;
        }
    }

    @Transactional
    public NewSchool createSchool(String schoolName, String subdomain, String adminEmail,
                                   String adminPassword, String adminFullName) {
        if (tenantRepository.existsBySubdomain(subdomain)) {
            throw new DuplicateSubdomainException(subdomain);
        }
        if (userRepository.existsByEmail(adminEmail)) {
            throw new DuplicateEmailException(adminEmail);
        }

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName(schoolName);
        tenant.setSubdomain(subdomain);
        tenant.setActive(true);
        tenant.setTier(TenantTier.FULL_SMS);
        tenant.setCreatedAt(Instant.now());
        tenant.setOnboardingCompleted(false);
        tenantRepository.save(tenant);

        int currentYear = Year.now().getValue();
        AcademicYear academicYear = new AcademicYear();
        academicYear.setId(UUID.randomUUID());
        academicYear.setTenantId(tenant.getId());
        academicYear.setName(currentYear + "-" + (currentYear + 1));
        academicYear.setStartDate(LocalDate.of(currentYear, 6, 1));
        academicYear.setEndDate(LocalDate.of(currentYear + 1, 5, 31));
        academicYear.setCurrent(true);
        academicYearRepository.save(academicYear);

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setTenantId(tenant.getId());
        admin.setAcademicYearId(academicYear.getId());
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName(adminFullName);
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        // Bootstrap admin — there's no PRINCIPAL/ADMIN yet to approve them.
        admin.setApprovalStatus(User.ApprovalStatus.APPROVED);
        userRepository.save(admin);

        subjectService.seedDefaultSubjectsIfNone(tenant.getId(), academicYear.getId());

        // Ensure a new school isn't left with zero classes if the admin never
        // completes a manual follow-up step.
        ClassSection firstSection = new ClassSection();
        firstSection.setId(UUID.randomUUID());
        firstSection.setTenantId(tenant.getId());
        firstSection.setAcademicYearId(academicYear.getId());
        firstSection.setGradeName("Grade 1");
        firstSection.setSectionName("A");
        classSectionRepository.save(firstSection);

        auditLogService.logDirect(tenant.getId(), academicYear.getId(), admin.getId(), admin.getEmail(),
                "SCHOOL_CREATED", "Tenant", tenant.getId(),
                "Created school \"" + schoolName + "\" (" + subdomain + ") with first admin " + adminEmail);

        return new NewSchool(tenant, academicYear, admin);
    }
}
