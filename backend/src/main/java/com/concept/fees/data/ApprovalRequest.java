package com.concept.fees.data;

import com.concept.common.BaseTenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An action an admin has asked for but may not carry out alone.
 *
 * <p>Reversing a payment un-records cash the school already receipted; editing
 * a fee plan re-prices a whole grade. Both were single-admin actions -- audited
 * afterwards, gated by nothing beforehand.
 *
 * <p>The requested change is held as JSON and replayed on approval, rather than
 * written to the live tables in a draft state. Both actions are wholesale
 * replacements, so replaying produces exactly the requested end state, and
 * until a principal decides, nothing has touched real data at all.
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseTenantEntity {

    public enum Action {
        PAYMENT_REVERSAL,
        FEE_PLAN_SAVE,
        FEE_PLAN_DELETE
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Action action;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    /** Human-readable line for the principal's queue -- what they are agreeing to. */
    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "requested_by_email")
    private String requestedByEmail;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public UUID getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(UUID requestedByUserId) { this.requestedByUserId = requestedByUserId; }

    public String getRequestedByEmail() { return requestedByEmail; }
    public void setRequestedByEmail(String requestedByEmail) { this.requestedByEmail = requestedByEmail; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getDecidedByUserId() { return decidedByUserId; }
    public void setDecidedByUserId(UUID decidedByUserId) { this.decidedByUserId = decidedByUserId; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
}
