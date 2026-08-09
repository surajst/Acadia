package com.concept.messaging.data;

import com.concept.messaging.data.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Message access for messaging. Messages carry no tenant of their own — they are
 * reached only through a {@link Conversation} the caller already resolved
 * tenant-scoped, so access control lives at the conversation boundary.
 */
@Repository
public interface MsgMessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    Message findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
