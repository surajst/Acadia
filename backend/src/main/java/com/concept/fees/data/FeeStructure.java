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
 * What one grade level costs at one school, for one academic year.
 *
 * <p>The uniqueness is deliberately the three columns together. grade_level
 * alone used to carry the constraint, which on a multi-tenant table means the
 * first school to configure "Grade 6" owns that grade level for everybody --
 * the same defect class as usernames keyed on a bare roll number. Including
 * the academic year is what lets a school raise its fees without rewriting the
 * row that last year's invoices were priced from.
 */
@Entity
@Table(name = "fee_structures", uniqueConstraints = @UniqueConstraint(
        name = "uk_fee_structures_tenant_year_grade",
        columnNames = {"tenant_id", "academic_year_id", "grade_level"}))
public class FeeStructure extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "grade_level", nullable = false)
    private String gradeLevel;

    @Column(name = "tuition_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal tuitionFee;

    @Column(name = "term_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal termFee;

    public FeeStructure() {}

    public FeeStructure(UUID id, String gradeLevel, BigDecimal tuitionFee, BigDecimal termFee) {
        this.id = id;
        this.gradeLevel = gradeLevel;
        this.tuitionFee = tuitionFee;
        this.termFee = termFee;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(String gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public BigDecimal getTuitionFee() {
        return tuitionFee;
    }

    public void setTuitionFee(BigDecimal tuitionFee) {
        this.tuitionFee = tuitionFee;
    }

    public BigDecimal getTermFee() {
        return termFee;
    }

    public void setTermFee(BigDecimal termFee) {
        this.termFee = termFee;
    }
}
