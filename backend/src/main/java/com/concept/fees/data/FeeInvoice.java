package com.concept.fees.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fee_invoices")
public class FeeInvoice extends BaseTenantEntity {

    public enum FeeStatus {
        UNPAID, PARTIALLY_PAID, PAID
    }

    public enum FeeWaiverStatus {
        NONE, PENDING, APPROVED, REJECTED
    }

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "amount_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private FeeStatus status;

    @Column(name = "waiver_amount", precision = 19, scale = 2)
    private BigDecimal waiverAmount;

    @Column(name = "waiver_reason", length = 500)
    private String waiverReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "waiver_status", length = 20)
    private FeeWaiverStatus waiverStatus = FeeWaiverStatus.NONE;

    /**
     * What the grade's fee structure said at the time this invoice was raised,
     * kept only when the admin billed something different.
     *
     * <p>Without it, totalAmount alone cannot answer "why was this family billed
     * 14,000 when Grade 6 costs 22,000" -- a deliberate concession, a fee change
     * since, and a typo all look identical after the fact. Null means the
     * invoice was priced straight from the fee structure.
     */
    @Column(name = "base_amount", precision = 19, scale = 2)
    private BigDecimal baseAmount;

    /** Required whenever baseAmount is set: a number without a reason is not an audit trail. */
    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    /** The admin who made the call, so the decision has a name against it. */
    @Column(name = "override_by", length = 255)
    private String overrideBy;

    /**
     * When this instalment falls due. Snapshotted at generation, the same way
     * the amount is: editing a plan afterwards must not silently move a date a
     * family has already been given.
     *
     * <p>Null on an invoice that belongs to no plan.
     */
    @Column(name = "due_date")
    private java.time.LocalDate dueDate;

    @Column(name = "fee_plan_instalment_id")
    private UUID feePlanInstalmentId;

    /** The school's own name for this instalment, copied so the invoice reads on its own. */
    @Column(name = "instalment_label")
    private String instalmentLabel;

    /**
     * PLAN for an invoice generated from a fee plan, CUSTOM for one an admin
     * composed by hand. Recording it keeps the ledger honest about which
     * figures came from the school's own fee plan and which were chosen for
     * this family, which is the first thing anyone asks when a bill is queried.
     */
    @Column(name = "source", length = 20)
    private String source;

    public FeeInvoice() {}

    public FeeInvoice(UUID id, UUID studentId, BigDecimal totalAmount, BigDecimal amountPaid) {
        this.id = id;
        this.studentId = studentId;
        this.totalAmount = totalAmount;
        this.amountPaid = amountPaid;
        updateBalances();
    }

    public void updateBalances() {
        BigDecimal paid = this.amountPaid != null ? this.amountPaid : BigDecimal.ZERO;
        BigDecimal total = this.totalAmount != null ? this.totalAmount : BigDecimal.ZERO;
        BigDecimal waiver = (this.waiverStatus == FeeWaiverStatus.APPROVED && this.waiverAmount != null)
                ? this.waiverAmount : BigDecimal.ZERO;

        BigDecimal effectiveTotal = total.subtract(waiver).max(BigDecimal.ZERO);
        this.amountDue = effectiveTotal.subtract(paid).max(BigDecimal.ZERO);

        BigDecimal covered = paid.add(waiver);
        if (covered.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = FeeStatus.UNPAID;
        } else if (covered.compareTo(total) >= 0) {
            this.status = FeeStatus.PAID;
        } else {
            this.status = FeeStatus.PARTIALLY_PAID;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        updateBalances();
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
        updateBalances();
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(BigDecimal amountDue) {
        this.amountDue = amountDue;
    }

    public FeeStatus getStatus() {
        return status;
    }

    public void setStatus(FeeStatus status) {
        this.status = status;
    }

    public BigDecimal getWaiverAmount() {
        return waiverAmount;
    }

    public void setWaiverAmount(BigDecimal waiverAmount) {
        this.waiverAmount = waiverAmount;
    }

    public String getWaiverReason() {
        return waiverReason;
    }

    public void setWaiverReason(String waiverReason) {
        this.waiverReason = waiverReason;
    }

    public FeeWaiverStatus getWaiverStatus() {
        return waiverStatus;
    }

    public void setWaiverStatus(FeeWaiverStatus waiverStatus) {
        this.waiverStatus = waiverStatus;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public String getOverrideBy() {
        return overrideBy;
    }

    public void setOverrideBy(String overrideBy) {
        this.overrideBy = overrideBy;
    }

    /** True when this invoice was billed at something other than the grade's fee structure. */
    public boolean isOverridden() {
        return baseAmount != null;
    }

    public java.time.LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(java.time.LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public UUID getFeePlanInstalmentId() {
        return feePlanInstalmentId;
    }

    public void setFeePlanInstalmentId(UUID feePlanInstalmentId) {
        this.feePlanInstalmentId = feePlanInstalmentId;
    }

    public String getInstalmentLabel() {
        return instalmentLabel;
    }

    public void setInstalmentLabel(String instalmentLabel) {
        this.instalmentLabel = instalmentLabel;
    }

    /** Unpaid and past its due date. Nothing could ask this before due dates existed. */
    public boolean isOverdue(java.time.LocalDate today) {
        return dueDate != null
                && status != FeeStatus.PAID
                && dueDate.isBefore(today);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isCustom() {
        return "CUSTOM".equals(source);
    }
}
