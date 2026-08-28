package com.concept.parent.app;

import com.concept.academics.data.StudentMetric;
import com.concept.academics.data.StudentMetricRepository;
import com.concept.announcement.Announcement;
import com.concept.announcement.AnnouncementRepository;
import com.concept.fees.data.FeeInvoice;
import com.concept.fees.data.FeeInvoiceRepository;
import com.concept.fees.data.FeeTransaction;
import com.concept.fees.data.FeeTransactionRepository;
import com.concept.shared.data.Parent;
import com.concept.parent.data.ParentQuest;
import com.concept.parent.data.ParentQuestRepository;
import com.concept.parent.data.ParentReward;
import com.concept.parent.data.ParentRewardRepository;
import com.concept.shared.data.Student;
import com.concept.shared.data.StudentRepository;
import com.concept.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Application layer for the parent dashboard (ADR 0001). Runs the same
 * tenant-scoped queries the former god-controller did and flattens every JPA
 * entity into a view record, so the Thymeleaf template renders off flat data
 * and no persistence type reaches the interface layer.
 */
@Service
public class ParentDashboardService {

    private final ParentQuestRepository parentQuestRepository;
    private final ParentRewardRepository parentRewardRepository;
    private final StudentRepository studentRepository;
    private final AnnouncementRepository announcementRepository;
    private final StudentMetricRepository studentMetricRepository;
    private final FeeInvoiceRepository feeInvoiceRepository;
    private final FeeTransactionRepository feeTransactionRepository;
    private final CurrentUserService currentUserService;

    public ParentDashboardService(ParentQuestRepository parentQuestRepository,
                                  ParentRewardRepository parentRewardRepository,
                                  StudentRepository studentRepository,
                                  AnnouncementRepository announcementRepository,
                                  StudentMetricRepository studentMetricRepository,
                                  FeeInvoiceRepository feeInvoiceRepository,
                                  FeeTransactionRepository feeTransactionRepository,
                                  CurrentUserService currentUserService) {
        this.parentQuestRepository = parentQuestRepository;
        this.parentRewardRepository = parentRewardRepository;
        this.studentRepository = studentRepository;
        this.announcementRepository = announcementRepository;
        this.studentMetricRepository = studentMetricRepository;
        this.feeInvoiceRepository = feeInvoiceRepository;
        this.feeTransactionRepository = feeTransactionRepository;
        this.currentUserService = currentUserService;
    }

    public record ParentView(String firstName, String lastName) {}

    public record StudentView(UUID id, String firstName, String lastName, String className) {}

    public record MetricView(Integer schoolXp, Integer parentXp, Integer activeStreak) {}

    /** One instalment that still owes something. */
    public record DueLine(String label, BigDecimal amount, LocalDate dueDate, boolean overdue) {}

    /**
     * One row of the family's receipt history.
     *
     * @param reversal true for the negative row written when a payment is
     *                 undone. Shown rather than filtered: a parent who saw a
     *                 receipt appear and then disappear is owed the explanation,
     *                 and hiding it makes the running total look wrong.
     */
    public record PaymentLine(LocalDate paidOn, BigDecimal amount, String mode,
                              Integer receiptNumber, String label, boolean reversal) {}

    /**
     * What one child's fees look like to their parent.
     *
     * <p>Totals are pre-summed here rather than in the template: a parent
     * reading this wants "what do I owe and by when" first. The per-instalment
     * dues and the receipt history follow underneath, for the parent who wants
     * to check a specific payment landed.
     *
     * @param overdueCount instalments already past their due date and not settled
     * @param nextDueLabel e.g. "Term 2", or null when nothing is outstanding
     * @param dues         only the instalments still owing, soonest first
     * @param payments     every receipt against this child, newest first
     */
    public record FeeView(BigDecimal totalBilled,
                          BigDecimal totalPaid,
                          BigDecimal totalDue,
                          int instalmentCount,
                          int paidCount,
                          int overdueCount,
                          String nextDueLabel,
                          LocalDate nextDueDate,
                          BigDecimal nextDueAmount,
                          List<DueLine> dues,
                          List<PaymentLine> payments) {

        /** True when there is nothing left to pay -- the template shows a settled state. */
        public boolean settled() {
            return totalDue == null || totalDue.signum() <= 0;
        }
    }

    public record QuestView(UUID id, String studentFirstName, String taskDescription, Integer xpBounty) {}

    public record RewardView(UUID id, String studentFirstName, String rewardTitle) {}

    public record AnnouncementView(String title, String content, String targetGrade, LocalDateTime createdAt) {}

    /** Everything the parent_dashboard template needs, all flat. */
    public record ParentDashboardView(ParentView parent,
                                      /**
                                       * Quests the parent set that the child has
                                       * not finished yet. Without these the
                                       * dashboard showed nothing at all until a
                                       * child submitted work, so a parent had no
                                       * confirmation their quest existed.
                                       */
                                      List<QuestView> activeQuests,
                                      List<QuestView> awaitingQuests,
                                      List<RewardView> awaitingRewards,
                                      List<AnnouncementView> announcements,
                                      List<StudentView> students,
                                      Map<String, MetricView> studentMetrics,
                                      Map<String, Long> pendingQuestCounts,
                                      /**
                                       * Fees per child, keyed by student id as a
                                       * String so the template can index it the
                                       * same way it indexes studentMetrics.
                                       * A child with no invoices raised yet is
                                       * absent from the map rather than present
                                       * with zeroes -- "nothing billed" and
                                       * "billed and settled" are different things
                                       * to say to a parent.
                                       */
                                      Map<String, FeeView> studentFees) {}

    /** Empty when the caller has no linked parent record. */
    public Optional<ParentDashboardView> dashboard(Authentication authentication) {
        Parent parent = currentUserService.getCurrentParent(authentication).orElse(null);
        if (parent == null) {
            return Optional.empty();
        }

        List<ParentQuest> awaitingQuests =
                parentQuestRepository.findByParentIdAndStatus(parent.getId(), "COMPLETED_AWAITING_APPROVAL");
        // PENDING is what assignTask sets: assigned, not yet done.
        List<ParentQuest> activeQuests =
                parentQuestRepository.findByParentIdAndStatus(parent.getId(), "PENDING");
        List<ParentReward> awaitingRewards =
                parentRewardRepository.findByParentIdAndStatus(parent.getId(), "CLAIMED_AWAITING_DELIVERY");

        List<Student> students = studentRepository.findByParentsContaining(parent);
        List<String> targetGrades = new ArrayList<>();
        targetGrades.add("ALL");
        for (Student student : students) {
            if (student.getClassSection() != null && student.getClassSection().getGradeName() != null) {
                targetGrades.add(student.getClassSection().getGradeName());
            }
        }

        UUID tenantId = parent.getTenantId();
        UUID academicYearId = parent.getAcademicYearId();
        if ((tenantId == null || academicYearId == null) && !students.isEmpty()) {
            // Fall back to this parent's own linked child's tenant/year, never
            // to an arbitrary student from another family/tenant.
            Student first = students.get(0);
            tenantId = first.getTenantId();
            academicYearId = first.getAcademicYearId();
        }

        List<Announcement> announcements = announcementRepository.findByTenantIdAndAcademicYearIdAndTargetGradeIn(
                tenantId, academicYearId, targetGrades);
        announcements.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        Map<String, MetricView> studentMetrics = new LinkedHashMap<>();
        Map<String, Long> pendingQuestCounts = new LinkedHashMap<>();
        for (Student s : students) {
            String studentIdStr = s.getId().toString();
            studentMetricRepository.findByStudentId(s.getId())
                    .ifPresent(m -> studentMetrics.put(studentIdStr, toMetricView(m)));
            long pendingCount = awaitingQuests.stream()
                    .filter(q -> q.getStudent() != null && s.getId().equals(q.getStudent().getId()))
                    .count();
            pendingQuestCounts.put(studentIdStr, pendingCount);
        }

        Map<String, FeeView> studentFees = feesFor(students, tenantId);

        ParentDashboardView view = new ParentDashboardView(
                new ParentView(parent.getFirstName(), parent.getLastName()),
                activeQuests.stream().map(this::toQuestView).toList(),
                awaitingQuests.stream().map(this::toQuestView).toList(),
                awaitingRewards.stream().map(this::toRewardView).toList(),
                announcements.stream().map(this::toAnnouncementView).toList(),
                students.stream().map(ParentDashboardService::toStudentView).toList(),
                studentMetrics,
                pendingQuestCounts,
                studentFees);
        return Optional.of(view);
    }

    /**
     * Sums each child's invoices into one line a parent can act on.
     *
     * <p>Scoped twice on purpose. The children come from
     * {@code findByParentsContaining}, so they are this parent's by
     * construction, and the query is still confined to the tenant -- neither
     * alone is enough if the other is ever refactored away.
     */
    private Map<String, FeeView> feesFor(List<Student> students, UUID tenantId) {
        Map<String, FeeView> fees = new LinkedHashMap<>();
        if (students.isEmpty() || tenantId == null) {
            return fees;
        }
        List<UUID> ids = students.stream().map(Student::getId).toList();
        List<FeeInvoice> invoices = feeInvoiceRepository.findByStudentIdInAndTenantId(ids, tenantId);
        if (invoices.isEmpty()) {
            return fees;
        }

        LocalDate today = LocalDate.now();
        Map<UUID, List<FeeInvoice>> byStudent = new LinkedHashMap<>();
        for (FeeInvoice inv : invoices) {
            byStudent.computeIfAbsent(inv.getStudentId(), k -> new ArrayList<>()).add(inv);
        }

        // One query for the whole family's receipts, then grouped in memory --
        // a parent with three children should not cost three round trips.
        Map<UUID, String> labelByInvoice = new LinkedHashMap<>();
        for (FeeInvoice inv : invoices) {
            labelByInvoice.put(inv.getId(), inv.getInstalmentLabel());
        }
        Map<UUID, List<FeeTransaction>> txByInvoice = new LinkedHashMap<>();
        for (FeeTransaction tx : feeTransactionRepository
                .findByInvoiceIdInAndTenantIdOrderByPaidAtDesc(labelByInvoice.keySet(), tenantId)) {
            txByInvoice.computeIfAbsent(tx.getInvoiceId(), k -> new ArrayList<>()).add(tx);
        }

        for (Student s : students) {
            List<FeeInvoice> own = byStudent.get(s.getId());
            if (own == null || own.isEmpty()) {
                continue;
            }
            BigDecimal billed = BigDecimal.ZERO;
            BigDecimal paid = BigDecimal.ZERO;
            BigDecimal due = BigDecimal.ZERO;
            int paidCount = 0;
            int overdue = 0;
            FeeInvoice next = null;
            List<DueLine> dues = new ArrayList<>();
            List<PaymentLine> payments = new ArrayList<>();

            for (FeeInvoice inv : own) {
                billed = billed.add(nz(inv.getTotalAmount()));
                paid = paid.add(nz(inv.getAmountPaid()));
                BigDecimal owing = nz(inv.getAmountDue());
                due = due.add(owing);

                for (FeeTransaction tx : txByInvoice.getOrDefault(inv.getId(), List.of())) {
                    payments.add(new PaymentLine(
                            tx.getPaidAt() == null ? null : tx.getPaidAt().toLocalDate(),
                            tx.getAmountPaid(), tx.getPaymentMode(), tx.getReceiptNumber(),
                            inv.getInstalmentLabel(), tx.isReversal()));
                }

                if (owing.signum() <= 0) {
                    paidCount++;
                    continue;
                }
                boolean isOverdue = inv.getDueDate() != null && inv.getDueDate().isBefore(today);
                if (isOverdue) {
                    overdue++;
                }
                dues.add(new DueLine(inv.getInstalmentLabel(), owing, inv.getDueDate(), isOverdue));
                // The soonest unsettled instalment is the one worth naming.
                if (next == null || earlier(inv.getDueDate(), next.getDueDate())) {
                    next = inv;
                }
            }

            // Soonest first for what is owed; newest first for what has been paid.
            dues.sort((a, b) -> {
                if (a.dueDate() == null) return b.dueDate() == null ? 0 : 1;
                if (b.dueDate() == null) return -1;
                return a.dueDate().compareTo(b.dueDate());
            });
            payments.sort((a, b) -> {
                if (a.paidOn() == null) return b.paidOn() == null ? 0 : 1;
                if (b.paidOn() == null) return -1;
                return b.paidOn().compareTo(a.paidOn());
            });

            fees.put(s.getId().toString(), new FeeView(
                    billed, paid, due, own.size(), paidCount, overdue,
                    next == null ? null : next.getInstalmentLabel(),
                    next == null ? null : next.getDueDate(),
                    next == null ? null : nz(next.getAmountDue()),
                    dues, payments));
        }
        return fees;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** A missing due date sorts last, so a dated instalment is always preferred. */
    private static boolean earlier(LocalDate candidate, LocalDate current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isBefore(current);
    }

    private static StudentView toStudentView(Student s) {
        String className = (s.getClassSection() != null)
                ? s.getClassSection().getGradeName() + " — " + s.getClassSection().getSectionName()
                : "Class N/A";
        return new StudentView(s.getId(), s.getFirstName(), s.getLastName(), className);
    }

    private MetricView toMetricView(StudentMetric m) {
        return new MetricView(m.getSchoolXp(), m.getParentXp(), m.getActiveStreak());
    }

    private QuestView toQuestView(ParentQuest q) {
        String studentFirstName = q.getStudent() != null ? q.getStudent().getFirstName() : "";
        return new QuestView(q.getId(), studentFirstName, q.getTaskDescription(), q.getXpBounty());
    }

    private RewardView toRewardView(ParentReward r) {
        String studentFirstName = r.getStudent() != null ? r.getStudent().getFirstName() : "";
        return new RewardView(r.getId(), studentFirstName, r.getRewardTitle());
    }

    private AnnouncementView toAnnouncementView(Announcement a) {
        return new AnnouncementView(a.getTitle(), a.getContent(), a.getTargetGrade(), a.getCreatedAt());
    }
}
