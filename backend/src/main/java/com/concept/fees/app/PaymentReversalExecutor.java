package com.concept.fees.app;

import com.concept.fees.data.ApprovalRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reverses the payment once a principal has agreed to it.
 *
 * <p>The guards in {@code FeeManagementService.reversePaymentApproved} run now,
 * not when the request was raised -- so a payment reversed by some other route
 * while this sat in the queue fails here, and the request stays pending rather
 * than recording a reversal that never happened.
 */
@Component
public class PaymentReversalExecutor implements ApprovalExecutor {

    /** What the admin asked for, replayed verbatim at approval time. */
    public record Payload(UUID transactionId, String reason) {}

    private final FeeManagementService feeManagementService;
    private final ObjectMapper objectMapper;

    public PaymentReversalExecutor(FeeManagementService feeManagementService, ObjectMapper objectMapper) {
        this.feeManagementService = feeManagementService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ApprovalRequest.Action action() {
        return ApprovalRequest.Action.PAYMENT_REVERSAL;
    }

    @Override
    public void execute(String payloadJson, UUID tenantId, Authentication authentication) {
        Payload payload = read(payloadJson);
        feeManagementService.reversePaymentApproved(
                payload.transactionId(), payload.reason(), tenantId, authentication);
    }

    private Payload read(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Payload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the recorded reversal request: " + e.getMessage());
        }
    }
}
