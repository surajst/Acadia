package com.concept.fees.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fee_transactions")
public class FeeTransaction extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "payment_mode", nullable = false, length = 50)
    private String paymentMode;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    /**
     * Set when this row undoes an earlier payment, pointing at the row it
     * reverses. A correction is a new transaction rather than an edit: the
     * mistake and its correction both stay visible, and this is what makes a
     * second reversal of the same payment detectable.
     */
    @Column(name = "reverses_transaction_id")
    private UUID reversesTransactionId;

    /** Why. Required on a reversal -- an unexplained negative line is not an audit trail. */
    @Column(name = "note", length = 500)
    private String note;

    /**
     * Sequential per school per year, starting at 1. Null on a reversal: it is
     * a correction to a payment already receipted, not a new one collected
     * across the counter, and giving it its own number would make the
     * sequence overstate how much money actually changed hands.
     */
    @Column(name = "receipt_number")
    private Integer receiptNumber;

    public FeeTransaction() {}

    public FeeTransaction(UUID id, UUID invoiceId, BigDecimal amountPaid, String paymentMode, LocalDateTime paidAt) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.amountPaid = amountPaid;
        this.paymentMode = paymentMode;
        this.paidAt = paidAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public UUID getReversesTransactionId() {
        return reversesTransactionId;
    }

    public void setReversesTransactionId(UUID reversesTransactionId) {
        this.reversesTransactionId = reversesTransactionId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isReversal() {
        return reversesTransactionId != null;
    }

    public Integer getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(Integer receiptNumber) {
        this.receiptNumber = receiptNumber;
    }
}
