package com.concept.fees.app;

import com.concept.common.AuditLogService;
import com.concept.fees.data.ApprovalRequest;
import com.concept.fees.data.ApprovalRequestRepository;
import com.concept.user.CurrentUserService;
import com.concept.user.User;
import com.concept.user.UserRepository;
import com.concept.user.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds actions an admin has asked for until a principal decides.
 *
 * <p>Applies to reversing a payment and to changing a fee plan. Both were
 * single-admin actions: one un-records cash the school already receipted, the
 * other re-prices a whole grade. Neither has a value threshold -- the school's
 * rule is that they always need a second person, so there is no amount to
 * configure and no line to argue about.
 *
 * <p>Nothing here knows what the actions do. {@link ApprovalExecutor}
 * implementations carry that out on approval, which keeps this class free of
 * fee mechanics and lets a new gated action be added without touching it.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final List<ApprovalExecutor> executors;
    private final UserRepository userRepository;

    public ApprovalService(ApprovalRequestRepository approvalRequestRepository,
                           CurrentUserService currentUserService,
                           AuditLogService auditLogService,
                           ObjectMapper objectMapper,
                           List<ApprovalExecutor> executors,
                           UserRepository userRepository) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.executors = executors;
        this.userRepository = userRepository;
    }

    /**
     * Records a request. Deliberately returns the stored row rather than the
     * outcome of the action -- at this point nothing has happened to any
     * invoice, payment or plan.
     */
    @Transactional
    public ApprovalRequest request(ApprovalRequest.Action action, Object payload, String summary,
                                   UUID tenantId, Authentication authentication) {
        User requester = currentUserService.getCurrentUser(authentication).orElse(null);
        if (requester == null || tenantId == null) {
            throw new IllegalArgumentException("Could not identify who is making this request.");
        }

        // A school that self-onboards gets one admin and no principal. Without
        // this the request would be accepted and then sit in a queue nobody can
        // ever decide -- so refuse up front, and say what to do about it. The
        // alternative, letting it through when no principal exists, would make
        // the whole gate optional by simply not appointing one.
        if (userRepository.countByRoleAndTenantId(UserRole.PRINCIPAL, tenantId) == 0) {
            throw new IllegalArgumentException(
                    "This needs a principal to approve it, and this school does not have one yet. "
                            + "Add a principal from the Staff Registry first.");
        }

        ApprovalRequest row = new ApprovalRequest();
        row.setId(UUID.randomUUID());
        row.setTenantId(tenantId);
        row.setAcademicYearId(requester.getAcademicYearId());
        row.setAction(action);
        row.setPayloadJson(serialise(payload));
        row.setSummary(summary);
        row.setRequestedByUserId(requester.getId());
        row.setRequestedByEmail(requester.getEmail());
        row.setRequestedAt(LocalDateTime.now());
        row.setStatus(ApprovalRequest.Status.PENDING);
        approvalRequestRepository.saveAndFlush(row);

        auditLogService.log(authentication, "APPROVAL_REQUESTED", "ApprovalRequest", row.getId(),
                action + ": " + summary);
        return row;
    }

    public List<ApprovalRequest> pending(UUID tenantId) {
        return approvalRequestRepository.findByTenantIdAndStatusOrderByRequestedAtAsc(
                tenantId, ApprovalRequest.Status.PENDING);
    }

    /**
     * Carries out the requested action, then marks the request approved. In
     * that order and in one transaction: if the action fails -- because the
     * payment was reversed by some other route while this sat in the queue --
     * the request stays pending rather than being marked approved for something
     * that never happened.
     */
    @Transactional
    public ApprovalRequest approve(UUID requestId, UUID tenantId, Authentication authentication) {
        ApprovalRequest row = requirePending(requestId, tenantId);

        // Same rule as waivers: the approve endpoint must not be a way for the
        // requester to wave their own request through.
        UUID actorId = currentUserService.getCurrentUser(authentication).map(User::getId).orElse(null);
        if (actorId != null && actorId.equals(row.getRequestedByUserId())) {
            throw new IllegalArgumentException(
                    "You raised this request, so someone else has to approve it.");
        }

        executorFor(row.getAction()).execute(row.getPayloadJson(), tenantId, authentication);

        row.setStatus(ApprovalRequest.Status.APPROVED);
        row.setDecidedByUserId(actorId);
        row.setDecidedAt(LocalDateTime.now());
        approvalRequestRepository.saveAndFlush(row);

        auditLogService.log(authentication, "APPROVAL_GRANTED", "ApprovalRequest", requestId,
                row.getAction() + ": " + row.getSummary());
        return row;
    }

    @Transactional
    public ApprovalRequest reject(UUID requestId, String reason, UUID tenantId, Authentication authentication) {
        ApprovalRequest row = requirePending(requestId, tenantId);

        row.setStatus(ApprovalRequest.Status.REJECTED);
        row.setDecidedByUserId(currentUserService.getCurrentUser(authentication).map(User::getId).orElse(null));
        row.setDecidedAt(LocalDateTime.now());
        row.setDecisionReason(reason);
        approvalRequestRepository.saveAndFlush(row);

        auditLogService.log(authentication, "APPROVAL_REJECTED", "ApprovalRequest", requestId,
                row.getAction() + ": " + row.getSummary() + " — " + (reason == null ? "no reason given" : reason));
        return row;
    }

    private ApprovalRequest requirePending(UUID requestId, UUID tenantId) {
        ApprovalRequest row = approvalRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found."));
        if (row.getStatus() != ApprovalRequest.Status.PENDING) {
            throw new IllegalArgumentException("That request has already been decided.");
        }
        return row;
    }

    private ApprovalExecutor executorFor(ApprovalRequest.Action action) {
        Optional<ApprovalExecutor> match = executors.stream()
                .filter(e -> e.action() == action)
                .findFirst();
        return match.orElseThrow(() -> new IllegalStateException(
                "No executor registered for " + action + " — approving it would silently do nothing."));
    }

    private String serialise(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not record this request: " + e.getMessage());
        }
    }
}
