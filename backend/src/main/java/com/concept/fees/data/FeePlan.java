package com.concept.fees.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a grade costs for a year, and — through its instalments — how that total
 * is collected.
 *
 * <p>Replaces FeeStructure, which held an amount and nothing else. Nobody pays
 * three to five lakh at once, so an annual figure with no schedule attached
 * could not describe how any real school bills. The rename is the idea: the
 * missing information was never the number.
 *
 * <p>Unique per school AND per year, so raising fees does not mean rewriting
 * the row last year's invoices were priced from.
 */
@Entity
@Table(name = "fee_plans", uniqueConstraints = @UniqueConstraint(
        name = "uk_fee_plans_tenant_year_grade",
        columnNames = {"tenant_id", "academic_year_id", "grade_level"}))
public class FeePlan extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "grade_level", nullable = false)
    private String gradeLevel;

    @Column(name = "annual_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal annualAmount;

    public FeePlan() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }

    public BigDecimal getAnnualAmount() { return annualAmount; }
    public void setAnnualAmount(BigDecimal annualAmount) { this.annualAmount = annualAmount; }
}
