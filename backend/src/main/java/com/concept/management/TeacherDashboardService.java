package com.concept.management;
import com.concept.shared.data.ClassSection;
import com.concept.shared.data.StudentRepository;
import com.concept.shared.data.Student;

import com.concept.user.User;
import com.concept.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the teacher dashboard's two verification queues (pending milestone
 * submissions and pending progress) and enforces the ownership scoping that
 * limits a plain TEACHER to their own students while keeping the tenant-wide
 * view for ADMIN/PRINCIPAL. Extracted from UnifiedDashboardWebController so this
 * security-sensitive filtering has one home and is unit-testable.
 */
@Service
public class TeacherDashboardService {

    private final UserRepository userRepository;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicSubmissionRepository academicSubmissionRepository;
    private final StudentProgressRepository studentProgressRepository;

    public TeacherDashboardService(UserRepository userRepository,
                                   SubjectAssignmentRepository subjectAssignmentRepository,
                                   StudentRepository studentRepository,
                                   AcademicSubmissionRepository academicSubmissionRepository,
                                   StudentProgressRepository studentProgressRepository) {
        this.userRepository = userRepository;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.studentRepository = studentRepository;
        this.academicSubmissionRepository = academicSubmissionRepository;
        this.studentProgressRepository = studentProgressRepository;
    }

    /** The two pending-review queues shown on the teacher dashboard. */
    public record VerificationQueues(List<MilestoneSubmissionDto> pendingSubmissions,
                                     List<StudentProgressDto> pendingProgress) {}

    /**
     * Students actually taught by this teacher, via real SubjectAssignment
     * records. Returns {@code null} if the user can't be resolved (caller then
     * applies no ownership filter), or an empty set if the teacher has no
     * assignments (nothing is theirs).
     */
    public Set<UUID> resolveOwnStudentIds(String userEmail) {
        User teacher = userRepository.findByEmail(userEmail).orElse(null);
        if (teacher == null) return null;
        List<ClassSection> sections = subjectAssignmentRepository.findByTeacher(teacher).stream()
                .map(SubjectAssignment::getClassSection).distinct().collect(Collectors.toList());
        if (sections.isEmpty()) return Collections.emptySet();
        return studentRepository.findByClassSectionIn(sections).stream()
                .map(Student::getId).collect(Collectors.toSet());
    }

    /**
     * Build both verification queues for the tenant. When {@code role} is TEACHER
     * the queues are filtered to the caller's own students; ADMIN/PRINCIPAL keep
     * the full tenant-wide oversight view.
     */
    public VerificationQueues buildVerificationQueues(String userEmail, String role, UUID tenantId) {
        boolean scopeToOwn = "TEACHER".equals(role);
        Set<UUID> ownStudentIds = scopeToOwn ? resolveOwnStudentIds(userEmail) : null;

        // ── Pending milestone submissions ───────────────────────────────────
        List<AcademicSubmission> rawSubmissions = tenantId != null
                ? academicSubmissionRepository.findByStatusAndStudentTenantId("PENDING", tenantId)
                : Collections.emptyList();
        if (scopeToOwn && ownStudentIds != null) {
            rawSubmissions = rawSubmissions.stream()
                    .filter(sub -> ownStudentIds.contains(sub.getStudentId()))
                    .collect(Collectors.toList());
        }

        List<MilestoneSubmissionDto> pendingSubmissions = rawSubmissions.stream()
                .map(sub -> {
                    Student student = studentRepository.findById(sub.getStudentId()).orElse(null);
                    String studentName = student != null ? student.getFirstName() + " " + student.getLastName() : "Unknown Student";
                    return new MilestoneSubmissionDto(sub.getId(), studentName, sub.getSkillName(), sub.getXpBounty(),
                            sub.getSubmittedAt(), sub.getProofOfWorkNotes(), sub.getAnswer1(), sub.getAnswer2(), sub.getAnswer3());
                })
                .collect(Collectors.toList());

        // ── Pending progress ────────────────────────────────────────────────
        List<StudentProgress> rawProgress = tenantId != null
                ? studentProgressRepository.findByStudentTenantIdAndStatus(tenantId, "PENDING")
                : Collections.emptyList();
        if (scopeToOwn && ownStudentIds != null) {
            rawProgress = rawProgress.stream()
                    .filter(sp -> sp.getStudent() != null && ownStudentIds.contains(sp.getStudent().getId()))
                    .collect(Collectors.toList());
        }

        List<StudentProgressDto> pendingProgress = new ArrayList<>();
        for (StudentProgress sp : rawProgress) {
            Student student = sp.getStudent();
            Curriculum curriculum = sp.getCurriculum();
            String studentName = student != null ? student.getFirstName() + " " + student.getLastName() : "Unknown Student";
            String subjectName = curriculum != null && curriculum.getSubjectCode() != null ? curriculum.getSubjectCode() : "Unknown";
            String topicName = curriculum != null ? curriculum.getTopicName() : "Unknown Topic";
            pendingProgress.add(new StudentProgressDto(sp.getId(), studentName, subjectName, topicName, sp.getCompletedAt()));
        }
        pendingProgress.sort((a, b) -> {
            if (a.getSubmittedAt() == null && b.getSubmittedAt() == null) return 0;
            if (a.getSubmittedAt() == null) return 1;
            if (b.getSubmittedAt() == null) return -1;
            return b.getSubmittedAt().compareTo(a.getSubmittedAt());
        });

        return new VerificationQueues(pendingSubmissions, pendingProgress);
    }

    public static class MilestoneSubmissionDto {
        private final UUID id;
        private final String studentName;
        private final String skillName;
        private final Integer xpBounty;
        private final LocalDateTime submittedAt;
        private final String proofOfWorkNotes;
        private final String answer1;
        private final String answer2;
        private final String answer3;

        public MilestoneSubmissionDto(UUID id, String studentName, String skillName, Integer xpBounty, LocalDateTime submittedAt, String proofOfWorkNotes, String answer1, String answer2, String answer3) {
            this.id = id;
            this.studentName = studentName;
            this.skillName = skillName;
            this.xpBounty = xpBounty;
            this.submittedAt = submittedAt;
            this.proofOfWorkNotes = proofOfWorkNotes;
            this.answer1 = answer1;
            this.answer2 = answer2;
            this.answer3 = answer3;
        }

        public UUID getId() { return id; }
        public String getStudentName() { return studentName; }
        public String getSkillName() { return skillName; }
        public Integer getXpBounty() { return xpBounty; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public String getProofOfWorkNotes() { return proofOfWorkNotes; }
        public String getAnswer1() { return answer1; }
        public String getAnswer2() { return answer2; }
        public String getAnswer3() { return answer3; }
    }

    public static class StudentProgressDto {
        private final UUID id;
        private final String studentName;
        private final String subjectName;
        private final String topicName;
        private final LocalDateTime submittedAt;

        public StudentProgressDto(UUID id, String studentName, String subjectName, String topicName, LocalDateTime submittedAt) {
            this.id = id;
            this.studentName = studentName;
            this.subjectName = subjectName;
            this.topicName = topicName;
            this.submittedAt = submittedAt;
        }

        public UUID getId() { return id; }
        public String getStudentName() { return studentName; }
        public String getSubjectName() { return subjectName; }
        public String getTopicName() { return topicName; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
    }
}
