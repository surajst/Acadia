package com.concept.billing.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One scheduled part of a {@link FeePlan}.
 *
 * <p>How many instalments a plan has, what each is worth and when each falls
 * due are entirely the school's choice. Three termly parts and twelve monthly
 * ones are the same structure with different rows, and no month, count or split
 * is assumed anywhere in code.
 *
 * <p>The amount is stored explicitly rather than as a percentage of the annual
 * total. Percentages have to be rounded, and rounding money produces a plan
 * whose parts do not add up to the whole — which a parent will notice before
 * anyone else does.
 */
@Entity
@Table(name = "fee_plan_instalments")
public class FeePlanInstalment extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "fee_plan_id", nullable = false)
    private UUID feePlanId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    /** What the school calls it — "Term 1", "April", "First instalment". */
    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Days from the student's billing start, not a calendar date.
     *
     * <p>A student admitted in September cannot owe an instalment that fell due
     * in April. Storing an offset lets the concrete date be resolved per
     * student when their invoices are raised — from the academic year for one
     * who starts with it, from the admission date for one who joins mid-year.
     * A hardcoded calendar would make every mid-year admission a manual
     * correction, which is how a family gets billed for a term their child was
     * not enrolled in.
     */
    @Column(name = "due_offset_days", nullable = false)
    private int dueOffsetDays;

    public FeePlanInstalment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFeePlanId() { return feePlanId; }
    public void setFeePlanId(UUID feePlanId) { this.feePlanId = feePlanId; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public int getDueOffsetDays() { return dueOffsetDays; }
    public void setDueOffsetDays(int dueOffsetDays) { this.dueOffsetDays = dueOffsetDays; }
}
