package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fee_structures")
public class FeeStructure extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "grade_level", nullable = false, unique = true)
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
