package com.concept.tenant;

import com.concept.academics.SubjectService;
import com.concept.common.AuditLogService;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.ClassSectionRepository;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
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
                                   String adminPassword, String adminFullName,
                                   SchoolType schoolType) {
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
        // Null would resolve to SECONDARY anyway, but recording what the school
        // told us is not the same as falling back -- the difference matters when
        // asking later why a tenant is set the way it is.
        tenant.setSchoolType(schoolType == null ? SchoolType.SECONDARY : schoolType);
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

        // No placeholder class section is created — the admin defines their real
        // grades/sections in the setup wizard (Step 1), and class sections are
        // also auto-created on demand during roster/student import. A dummy
        // "Grade 1 A" only confused admins into thinking it was pre-configured.

        auditLogService.logDirect(tenant.getId(), academicYear.getId(), admin.getId(), admin.getEmail(),
                "SCHOOL_CREATED", "Tenant", tenant.getId(),
                "Created school \"" + schoolName + "\" (" + subdomain + ") with first admin " + adminEmail);

        return new NewSchool(tenant, academicYear, admin);
    }
}
