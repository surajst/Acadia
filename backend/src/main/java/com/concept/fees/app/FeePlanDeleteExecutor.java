package com.concept.fees.app;

import com.concept.fees.data.ApprovalRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Deletes the fee plan once a principal has agreed to it. */
@Component
public class FeePlanDeleteExecutor implements ApprovalExecutor {

    public record Payload(UUID planId) {}

    private final FeePlanService feePlanService;
    private final ObjectMapper objectMapper;

    public FeePlanDeleteExecutor(FeePlanService feePlanService, ObjectMapper objectMapper) {
        this.feePlanService = feePlanService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ApprovalRequest.Action action() {
        return ApprovalRequest.Action.FEE_PLAN_DELETE;
    }

    @Override
    public void execute(String payloadJson, UUID tenantId, Authentication authentication) {
        Payload payload = read(payloadJson);
        feePlanService.deletePlanApproved(payload.planId(), tenantId, authentication);
    }

    private Payload read(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Payload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the recorded fee plan request: " + e.getMessage());
        }
    }
}
