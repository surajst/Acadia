package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fee_invoices")
public class FeeInvoice extends BaseTenantEntity {

    public enum FeeStatus {
        UNPAID, PARTIALLY_PAID, PAID
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

        this.amountDue = total.subtract(paid);

        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = FeeStatus.UNPAID;
        } else if (paid.compareTo(total) >= 0) {
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
}
