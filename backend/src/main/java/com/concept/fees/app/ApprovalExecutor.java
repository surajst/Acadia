package com.concept.fees.app;

import com.concept.fees.data.ApprovalRequest;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * Carries out one kind of approved action.
 *
 * <p>Kept separate from {@link ApprovalService} so the queue mechanics stay
 * ignorant of fee mechanics, and so adding a gated action is a new class rather
 * than a new branch in a growing switch. An action with no executor fails loudly
 * on approval instead of quietly doing nothing.
 */
public interface ApprovalExecutor {

    ApprovalRequest.Action action();

    /**
     * @param payloadJson the request exactly as it was made, replayed now
     * @param tenantId    the school, re-supplied rather than trusted from the payload
     */
    void execute(String payloadJson, UUID tenantId, Authentication authentication);
}
