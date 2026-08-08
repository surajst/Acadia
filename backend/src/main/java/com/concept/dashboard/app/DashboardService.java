package com.concept.dashboard.app;

import com.concept.dashboard.data.DashboardAttendanceRepository;
import com.concept.dashboard.data.DashboardClassSectionRepository;
import com.concept.dashboard.data.DashboardStudentRepository;
import com.concept.management.AdminProgressService;
import com.concept.management.AttendanceStatus;
import com.concept.management.ClassSection;
import com.concept.management.FeeManagementService;
import com.concept.management.Student;
import com.concept.management.TeacherDashboardService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application layer for the unified dashboard. Owns the roster query decisions
 * (class-specific vs. filtered search vs. assigned-section listing, with
 * pagination), the tenant-wide attendance stats, and — for PRINCIPAL — the
 * read-only school-wide rollups. Returns flat views so the web layer only maps
 * onto the model (ADR 0001).
 *
 * <p>The roster is only ever fetched scoped to a class, to the caller's assigned
 * sections, or to the tenant; the ADMIN-view fallback uses this tenant's own
 * sections, never an unscoped "first section anywhere".
 */
@Service
public class DashboardService {

    private final DashboardClassSectionRepository classSectionRepository;
    private final DashboardStudentRepository studentRepository;
    private final DashboardAttendanceRepository attendanceRepository;
    private final AdminProgressService adminProgressService;
    private final FeeManagementService feeManagementService;
    private final TeacherDashboardService teacherDashboardService;

    public DashboardService(DashboardClassSectionRepository classSectionRepository,
                            DashboardStudentRepository studentRepository,
                            DashboardAttendanceRepository attendanceRepository,
                            AdminProgressService adminProgressService,
                            FeeManagementService feeManagementService,
                            TeacherDashboardService teacherDashboardService) {
        this.classSectionRepository = classSectionRepository;
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.adminProgressService = adminProgressService;
        this.feeManagementService = feeManagementService;
        this.teacherDashboardService = teacherDashboardService;
    }

    @Transactional(readOnly = true)
    public RosterDashboardView buildRosterDashboard(UUID tenantId, String username, UUID classId,
                                                    String nameFilter, String gradeFilter,
                                                    int page, int size, boolean principal) {
        // Legacy teacher-id derivation preserved from the original controller.
        String effectiveUser = (username != null && !username.isBlank()) ? username : "teacher_1";
        UUID teacherId = UUID.nameUUIDFromBytes(effectiveUser.getBytes());

        // Every section visible to this tenant — the ADMIN-view fallback. Never
        // unscoped: falling back to "the first section found anywhere" would leak
        // another tenant's roster.
        List<ClassSection> checkSections = Collections.emptyList();
        try {
            checkSections = tenantId != null ? classSectionRepository.findByTenantId(tenantId) : Collections.emptyList();
        } catch (Exception e) {
            // gracefully catch
        }

        List<ClassSection> assignedClassrooms = Collections.emptyList();
        try {
            assignedClassrooms = classSectionRepository.findByTeacherIdAndTenantId(teacherId, tenantId);
        } catch (Exception e) {
            // gracefully catch
        }
        if (assignedClassrooms.isEmpty() && !checkSections.isEmpty()) {
            assignedClassrooms = checkSections;
        }

        // Normalise empty-string filter params to null so JPQL IS NULL checks work.
        String effectiveName = (nameFilter != null && !nameFilter.isBlank()) ? nameFilter.trim() : null;
        String effectiveGrade = (gradeFilter != null && !gradeFilter.isBlank()) ? gradeFilter.trim() : null;

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
                        .filter(s -> nameNeedle == null
                                || s.getFirstName().toLowerCase().contains(nameNeedle)
                                || s.getLastName().toLowerCase().contains(nameNeedle)
                                || (s.getFirstName() + " " + s.getLastName()).toLowerCase().contains(nameNeedle))
                        .filter(s -> effectiveGrade == null
                                || (s.getClassSection() != null
                                    && effectiveGrade.equals(s.getClassSection().getGradeName())))
                        .collect(Collectors.toList());
                totalRosterItems = conditionalRoster.size();
                totalRosterPages = 1;
            } else if (classId != null) {
                Page<Student> classPage = studentRepository.findBySchoolClassId(classId, pageable);
                conditionalRoster = classPage.getContent();
                totalRosterItems = classPage.getTotalElements();
                totalRosterPages = classPage.getTotalPages();
            } else if (effectiveName != null || effectiveGrade != null) {
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
                Page<Student> rosterPage = studentRepository.findByClassSectionIn(assignedClassrooms, pageable);
                conditionalRoster = rosterPage.getContent();
                totalRosterItems = rosterPage.getTotalElements();
                totalRosterPages = rosterPage.getTotalPages();
            }
        } catch (Exception e) {
            // gracefully catch
        }

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
        int attendancePercentage = totalStudents == 0 ? 0
                : (int) Math.round(((double) (totalStudents - activeAbsences) / totalStudents) * 100);

        Map<String, Object> schoolProgress = Collections.emptyMap();
        Map<String, Object> feeSummary = Collections.emptyMap();
        if (principal) {
            try {
                schoolProgress = adminProgressService.getSchoolWideProgress(tenantId);
            } catch (Exception e) {
                schoolProgress = Collections.emptyMap();
            }
            try {
                feeSummary = feeManagementService.getSchoolWideFeeSummary(tenantId);
            } catch (Exception e) {
                feeSummary = Collections.emptyMap();
            }
        }

        List<StudentRow> roster = conditionalRoster.stream()
                .map(s -> new StudentRow(s.getId(), s.getRollNumber(), s.getFirstName(), s.getLastName(),
                        s.getClassSection() != null ? s.getClassSection().getGradeName() : "",
                        s.getClassSection() != null ? s.getClassSection().getSectionName() : ""))
                .collect(Collectors.toList());

        return new RosterDashboardView(roster, allGradeNames, totalStudents, activeAbsences,
                attendancePercentage, totalRosterPages, totalRosterItems, schoolProgress, feeSummary);
    }

    @Transactional(readOnly = true)
    public TeacherDashboardView buildTeacherQueues(String email, String role, UUID tenantId) {
        TeacherDashboardService.VerificationQueues queues =
                teacherDashboardService.buildVerificationQueues(email, role, tenantId);

        List<TeacherTaskRow> submissions = queues.pendingSubmissions().stream()
                .map(t -> new TeacherTaskRow(t.getId(), t.getSkillName(), t.getXpBounty(), t.getStudentName(),
                        t.getSubmittedAt(), t.getProofOfWorkNotes(), t.getAnswer1(), t.getAnswer2(), t.getAnswer3()))
                .collect(Collectors.toList());

        List<TeacherProgressRow> progress = queues.pendingProgress().stream()
                .map(p -> new TeacherProgressRow(p.getId(), p.getSubjectName(), p.getTopicName(),
                        p.getStudentName(), p.getSubmittedAt()))
                .collect(Collectors.toList());

        return new TeacherDashboardView(submissions, progress);
    }
}
