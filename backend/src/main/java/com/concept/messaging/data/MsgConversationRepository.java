package com.concept.messaging.data;

import com.concept.common.TenantScopedRepository;
import com.concept.management.Conversation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped conversation access for messaging. Resolving a thread by id goes
 * through {@code findByIdAndTenantId}, so a message thread can never be opened
 * across tenants — the ownership check on top is defense-in-depth (ADR 0001).
 */
@Repository
public interface MsgConversationRepository extends TenantScopedRepository<Conversation, UUID> {

    List<Conversation> findByTeacherIdOrderByLastMessageAtDesc(UUID teacherId);

    List<Conversation> findByStudentIdInOrderByLastMessageAtDesc(List<UUID> studentIds);

    Optional<Conversation> findByStudentIdAndTeacherId(UUID studentId, UUID teacherId);
}
