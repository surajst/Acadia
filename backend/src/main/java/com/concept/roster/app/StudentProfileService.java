package com.concept.roster.app;

import com.concept.management.AttendanceRepository;
import com.concept.management.AttendanceStatus;
import com.concept.management.ClassSection;
import com.concept.management.ClassSectionRepository;
import com.concept.management.Parent;
import com.concept.management.SchoolClass;
import com.concept.management.SchoolClassRepository;
import com.concept.management.Student;
import com.concept.academics.StudentMetric;
import com.concept.academics.StudentMetricRepository;
import com.concept.roster.data.RosterStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application layer for the student-profile read. Owns the decision — including
 * the single, structural tenant check — and returns a {@link StudentProfileView}.
 * It knows nothing about HTTP: no request, no model, no template.
 */
@Service
public class StudentProfileService {

    private final RosterStudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final AttendanceRepository attendanceRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSectionRepository classSectionRepository;

    public StudentProfileService(RosterStudentRepository studentRepository,
                                 StudentMetricRepository studentMetricRepository,
                                 AttendanceRepository attendanceRepository,
                                 SchoolClassRepository schoolClassRepository,
                                 ClassSectionRepository classSectionRepository) {
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.attendanceRepository = attendanceRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.classSectionRepository = classSectionRepository;
    }

    /**
     * @param studentId the student being viewed
     * @param tenantId  the caller's tenant — the only tenant whose students are visible
     * @param username  the caller's login, used to scope the sidebar's classroom menu
     * @throws StudentProfileNotFoundException if the student is missing or in another tenant
     */
    @Transactional(readOnly = true)
    public StudentProfileView getProfile(UUID studentId, UUID tenantId, String username) {
        // Structural tenant isolation: no bare findById exists on this repository.
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentProfileNotFoundException(studentId));

        long presentCount = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.PRESENT);
        long absentCount = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.ABSENT);
        long totalDays = presentCount + absentCount;
        int attendancePercentage = totalDays == 0 ? 100 : (int) Math.round(((double) presentCount / totalDays) * 100);

        StudentMetric studentMetrics = studentMetricRepository.findByStudentId(studentId)
                .orElseGet(() -> zeroedMetric(student));

        List<ClassSection> availableClassesMenu = resolveClassroomMenu(tenantId, username);

        Parent primaryGuardian = null;
        int guardianCount = 0;
        Set<Parent> parents = student.getParents();
        if (parents != null) {
            guardianCount = parents.size();
            if (!parents.isEmpty()) {
                primaryGuardian = parents.iterator().next();
            }
        }

        int householdStreak = studentMetrics != null && studentMetrics.getActiveStreak() != null
                ? studentMetrics.getActiveStreak() : 0;

        List<SchoolClass> classList = tenantId != null
                ? schoolClassRepository.findByTenantId(tenantId) : Collections.emptyList();
        UUID currentSchoolClassId = student.getSchoolClass() != null ? student.getSchoolClass().getId() : null;

        return new StudentProfileView(
                student,
                presentCount,
                absentCount,
                attendancePercentage,
                studentMetrics,
                availableClassesMenu,
                primaryGuardian == null ? null
                        : (primaryGuardian.getFirstName() + " " + primaryGuardian.getLastName()).trim(),
                primaryGuardian == null ? null : primaryGuardian.getPhoneNumber(),
                primaryGuardian == null ? null : primaryGuardian.getId(),
                primaryGuardian == null ? "" : primaryGuardian.getFirstName(),
                primaryGuardian == null ? "" : primaryGuardian.getLastName(),
                guardianCount,
                householdStreak,
                classList,
                currentSchoolClassId);
    }

    /** Sidebar classroom menu: the caller's assigned sections, or the tenant's if none are assigned. */
    private List<ClassSection> resolveClassroomMenu(UUID tenantId, String username) {
        if (tenantId == null || username == null) {
            return Collections.emptyList();
        }
        UUID teacherId = UUID.nameUUIDFromBytes(username.getBytes());
        List<ClassSection> assigned = classSectionRepository.findByTeacherIdAndTenantId(teacherId, tenantId);
        if (assigned.isEmpty()) {
            return classSectionRepository.findByTenantId(tenantId);
        }
        return assigned;
    }

    /** A transient, non-persisted zeroed metric so a student with no metrics row still renders honest zeros. */
    private StudentMetric zeroedMetric(Student student) {
        StudentMetric m = new StudentMetric();
        m.setId(UUID.randomUUID());
        m.setStudent(student);
        m.setTenantId(student.getTenantId());
        m.setAcademicYearId(student.getAcademicYearId());
        m.setSchoolXp(0);
        m.setParentXp(0);
        m.setActiveStreak(0);
        return m;
    }
}
