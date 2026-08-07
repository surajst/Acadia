package com.concept.roster.app;

import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.management.AttendanceRepository;
import com.concept.management.AttendanceStatus;
import com.concept.management.ClassSection;
import com.concept.management.Parent;
import com.concept.management.SchoolClass;
import com.concept.management.SchoolClassRepository;
import com.concept.management.Student;
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
    private final SchoolClassRepository schoolClassRepository;

    public StudentProfileService(RosterStudentRepository studentRepository,
                                 StudentMetricRepository studentMetricRepository,
                                 AttendanceRepository attendanceRepository,
                                 SchoolClassRepository schoolClassRepository) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.attendanceRepository = attendanceRepository;
        this.schoolClassRepository = schoolClassRepository;
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
                : schoolClassRepository.findByTenantId(tenantId).stream()
                        .map(this::toOption)
                        .collect(Collectors.toList());
        UUID currentSchoolClassId = student.getSchoolClass() != null ? student.getSchoolClass().getId() : null;

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
                currentSchoolClassId);
    }

    private ClassOption toOption(SchoolClass c) {
        return new ClassOption(c.getId(), (c.getGradeLevel() + " - " + c.getSectionName()).trim());
    }
}
