package com.concept.fees.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One thing an invoice is charging for.
 *
 * <p>Before lines existed an invoice could say how much but never what for. A
 * trip, a textbook, a bus fare and a term's tuition were all just a larger
 * number, and the only way to charge for any of them was to override the
 * tuition amount — which destroyed the ability to answer the question a parent
 * actually asks.
 *
 * <p>The description is free text on purpose. Schools charge for things no
 * catalogue anticipates, and a fixed list of fee heads pushes them back to
 * abusing whichever field is nearest.
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    public InvoiceLine() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
