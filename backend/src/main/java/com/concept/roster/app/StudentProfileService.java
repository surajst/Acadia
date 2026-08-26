package com.concept.roster.app;

import com.concept.roster.data.RosterClassSectionRepository;
import com.concept.academics.data.StudentMetric;
import com.concept.academics.data.StudentMetricRepository;
import com.concept.shared.data.AttendanceRepository;
import com.concept.shared.data.AttendanceStatus;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.Parent;
import com.concept.shared.data.Student;
import com.concept.roster.data.RosterStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the student-profile read. Owns the decision — including
 * the single, structural tenant check — and returns a flat {@link StudentProfileView}.
 * It knows nothing about HTTP (no request, no model, no template) and lets no
 * entity escape: everything is mapped to plain values before it returns.
 */
@Service
public class StudentProfileService {

    private final RosterStudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final AttendanceRepository attendanceRepository;
    private final RosterClassSectionRepository classSectionRepository;
    private final PickupContactService pickupContactService;
    private final com.concept.user.UserRepository userRepository;

    public StudentProfileService(RosterStudentRepository studentRepository,
                                 StudentMetricRepository studentMetricRepository,
                                 AttendanceRepository attendanceRepository,
                                 RosterClassSectionRepository classSectionRepository,
                                 PickupContactService pickupContactService,
                                 com.concept.user.UserRepository userRepository) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.attendanceRepository = attendanceRepository;
        this.classSectionRepository = classSectionRepository;
        this.pickupContactService = pickupContactService;
        this.userRepository = userRepository;
    }

    /**
     * @param studentId the student being viewed
     * @param tenantId  the caller's tenant — the only tenant whose students are visible
     * @throws StudentProfileNotFoundException if the student is missing or in another tenant
     */
    @Transactional(readOnly = true)
    public StudentProfileView getProfile(UUID studentId, UUID tenantId) {
        // Structural tenant isolation: no bare findById exists on this repository.
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentProfileNotFoundException(studentId));

        long presentCount = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.PRESENT);
        long absentCount = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.ABSENT);
        long totalDays = presentCount + absentCount;
        int attendancePercentage = totalDays == 0 ? 100 : (int) Math.round(((double) presentCount / totalDays) * 100);

        StudentMetric metric = studentMetricRepository.findByStudentId(studentId).orElse(null);
        int schoolXp = metric != null && metric.getSchoolXp() != null ? metric.getSchoolXp() : 0;
        int parentXp = metric != null && metric.getParentXp() != null ? metric.getParentXp() : 0;
        int activeStreak = metric != null && metric.getActiveStreak() != null ? metric.getActiveStreak() : 0;

        ClassSection section = student.getClassSection();
        String gradeName = section != null ? section.getGradeName() : null;
        String sectionName = section != null ? section.getSectionName() : null;

        Parent primaryGuardian = null;
        int guardianCount = 0;
        Set<Parent> parents = student.getParents();
        if (parents != null) {
            guardianCount = parents.size();
            if (!parents.isEmpty()) {
                primaryGuardian = parents.iterator().next();
            }
        }

        List<ClassOption> classList = tenantId == null ? Collections.emptyList()
                : classSectionRepository.findByTenantId(tenantId).stream()
                        .map(this::toOption)
                        .collect(Collectors.toList());
        UUID currentClassSectionId = student.getClassSection() != null ? student.getClassSection().getId() : null;

        return new StudentProfileView(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getRollNumber(),
                gradeName,
                sectionName,
                presentCount,
                absentCount,
                attendancePercentage,
                schoolXp,
                parentXp,
                activeStreak,
                primaryGuardian == null ? null
                        : (primaryGuardian.getFirstName() + " " + primaryGuardian.getLastName()).trim(),
                primaryGuardian == null ? null : primaryGuardian.getPhoneNumber(),
                primaryGuardian == null ? null : primaryGuardian.getId(),
                primaryGuardian == null ? "" : primaryGuardian.getFirstName(),
                primaryGuardian == null ? "" : primaryGuardian.getLastName(),
                guardianCount,
                activeStreak,
                classList,
                currentClassSectionId,
                loginUsername(student.getUserId(), tenantId),
                primaryGuardian == null ? null : loginUsername(primaryGuardian.getUserId(), tenantId),
                student.getDateOfBirth(),
                ageIn(student.getDateOfBirth()),
                student.getMedicalNotes(),
                student.getEmergencyContactName(),
                student.getEmergencyContactPhone(),
                pickupContactService.forStudent(student.getId(), tenantId));
    }

    /**
     * The username a person signs in with, or null when no login was provisioned.
     * Scoped by tenant, not a bare findById: the architecture test rejects the
     * latter on a tenant-scoped repository, and it is right to -- reading a user
     * row by id alone is how cross-tenant leaks have started here before.
     */
    private String loginUsername(java.util.UUID userId, java.util.UUID tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .map(com.concept.user.User::getEmail).orElse(null);
    }

    private ClassOption toOption(ClassSection c) {
        return new ClassOption(c.getId(), (c.getGradeName() + " - " + c.getSectionName()).trim());
    }

    /**
     * Age in whole years, or null when no date of birth is on file. Computed
     * rather than stored: an age column is wrong the day after it is written,
     * and a preschool grouping children by age band needs today's answer.
     */
    private static Integer ageIn(java.time.LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.YEARS.between(dateOfBirth, java.time.LocalDate.now());
    }
}
