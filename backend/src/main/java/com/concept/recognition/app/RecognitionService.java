package com.concept.recognition.app;

import com.concept.academics.data.StudentMetric;
import com.concept.academics.data.StudentMetricRepository;
import com.concept.recognition.data.XpAward;
import com.concept.recognition.data.XpAwardRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lets a teacher recognise a child, and shows what they have been recognised for.
 *
 * <p>Before this, XP could only be granted by approving curriculum progress or
 * a milestone submission. A preschool sets neither, so its teachers had no way
 * to award anything and every XP figure on the parent dashboard stayed at zero.
 *
 * <p>The award and the running total are written in one transaction. They are
 * two representations of the same fact -- a total that disagrees with the list
 * of reasons behind it is worse than either alone, because a parent will spot
 * it and no one will be able to explain it.
 */
@Service
public class RecognitionService {

    /** A teacher may not hand out unbounded XP in one go. */
    private static final int MAX_POINTS = 100;

    private final XpAwardRepository xpAwardRepository;
    private final StudentRepository studentRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final CurrentUserService currentUserService;

    public RecognitionService(XpAwardRepository xpAwardRepository,
                              StudentRepository studentRepository,
                              StudentMetricRepository studentMetricRepository,
                              CurrentUserService currentUserService) {
        this.xpAwardRepository = xpAwardRepository;
        this.studentRepository = studentRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.currentUserService = currentUserService;
    }

    /** What a teacher can choose from, for rendering the picker. */
    public List<Badge> catalogue() {
        return List.of(Badge.values());
    }

    public record AwardView(UUID id, UUID studentId, String badgeCode, String label, String emoji,
                            int points, String reason, String awardedByName, LocalDateTime createdAt) {}

    /**
     * Recognises one child.
     *
     * @param reason optional; the badge's own suggested wording is used when blank
     * @throws IllegalArgumentException if the child is not in this tenant, or the badge is unknown
     */
    @Transactional
    public AwardView award(UUID studentId, String badgeCode, String reason,
                           UUID tenantId, Authentication authentication) {
        if (tenantId == null) {
            throw new IllegalArgumentException("No school context for this request.");
        }
        Badge badge = Badge.byCode(badgeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown award: " + badgeCode));

        // Tenant-scoped lookup, not findById: this is reached from a URL that
        // carries a student id, and a teacher must not be able to recognise --
        // or thereby confirm the existence of -- a child at another school.
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        User teacher = currentUserService.getCurrentUser(authentication).orElse(null);

        XpAward xpAward = new XpAward();
        xpAward.setId(UUID.randomUUID());
        xpAward.setTenantId(tenantId);
        xpAward.setAcademicYearId(student.getAcademicYearId());
        xpAward.setStudentId(student.getId());
        xpAward.setAwardedByUserId(teacher != null ? teacher.getId() : null);
        xpAward.setAwardedByName(teacher != null ? teacher.getFullName() : null);
        xpAward.setBadgeCode(badge.getCode());
        xpAward.setPoints(Math.min(badge.getPoints(), MAX_POINTS));
        xpAward.setReason(reason == null || reason.isBlank() ? badge.getSuggestion() : reason.trim());
        xpAward.setCreatedAt(LocalDateTime.now());
        xpAwardRepository.saveAndFlush(xpAward);

        StudentMetric metric = studentMetricRepository.findByStudentId(student.getId())
                .orElseGet(() -> newMetric(student));
        metric.setSchoolXp((metric.getSchoolXp() == null ? 0 : metric.getSchoolXp()) + xpAward.getPoints());
        studentMetricRepository.saveAndFlush(metric);

        return toView(xpAward, badge);
    }

    /** This child's recent recognition, newest first. */
    public List<AwardView> history(UUID studentId, UUID tenantId) {
        if (studentId == null || tenantId == null) {
            return List.of();
        }
        return xpAwardRepository.findTop20ByStudentIdAndTenantIdOrderByCreatedAtDesc(studentId, tenantId)
                .stream().map(this::toView).toList();
    }

    /** Recognition for several children at once, keyed by student id. */
    public Map<UUID, List<AwardView>> historyFor(Collection<UUID> studentIds, UUID tenantId) {
        Map<UUID, List<AwardView>> out = new LinkedHashMap<>();
        if (studentIds == null || studentIds.isEmpty() || tenantId == null) {
            return out;
        }
        for (XpAward a : xpAwardRepository.findByStudentIdInAndTenantIdOrderByCreatedAtDesc(studentIds, tenantId)) {
            out.computeIfAbsent(a.getStudentId(), k -> new ArrayList<>()).add(toView(a));
        }
        return out;
    }

    private StudentMetric newMetric(Student student) {
        StudentMetric metric = new StudentMetric();
        metric.setId(UUID.randomUUID());
        metric.setStudent(student);
        metric.setTenantId(student.getTenantId());
        metric.setAcademicYearId(student.getAcademicYearId());
        metric.setSchoolXp(0);
        metric.setParentXp(0);
        metric.setActiveStreak(0);
        return metric;
    }

    private AwardView toView(XpAward a) {
        return toView(a, Badge.byCode(a.getBadgeCode()).orElse(null));
    }

    private AwardView toView(XpAward a, Badge badge) {
        // A row whose badge has since been removed from the catalogue still
        // renders, with its stored code standing in for the label.
        return new AwardView(a.getId(), a.getStudentId(), a.getBadgeCode(),
                badge != null ? badge.getLabel() : a.getBadgeCode(),
                badge != null ? badge.getEmoji() : "🏅",
                a.getPoints(), a.getReason(), a.getAwardedByName(), a.getCreatedAt());
    }
}
