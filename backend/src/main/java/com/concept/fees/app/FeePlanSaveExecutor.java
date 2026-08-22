package com.concept.fees.app;

import com.concept.fees.data.ApprovalRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Writes the fee plan once a principal has agreed to it.
 *
 * <p>Replaying the payload is safe here because savePlan is a wholesale
 * replacement: it upserts the plan and rewrites the whole instalment schedule.
 * So applying it later produces exactly the state the admin asked for,
 * regardless of what the plan looked like when the request was raised.
 */
@Component
public class FeePlanSaveExecutor implements ApprovalExecutor {

    public record Payload(String gradeLevel,
                          List<FeePlanService.InstalmentSpec> instalments,
                          UUID academicYearId) {}

    private final FeePlanService feePlanService;
    private final ObjectMapper objectMapper;

    public FeePlanSaveExecutor(FeePlanService feePlanService, ObjectMapper objectMapper) {
        this.feePlanService = feePlanService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ApprovalRequest.Action action() {
        return ApprovalRequest.Action.FEE_PLAN_SAVE;
    }

    @Override
    public void execute(String payloadJson, UUID tenantId, Authentication authentication) {
        Payload payload = read(payloadJson);
        feePlanService.savePlanApproved(payload.gradeLevel(), payload.instalments(),
                tenantId, payload.academicYearId(), authentication);
    }

    private Payload read(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Payload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the recorded fee plan request: " + e.getMessage());
        }
    }
}
