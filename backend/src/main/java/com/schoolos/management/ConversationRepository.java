package com.schoolos.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByTeacherIdOrderByLastMessageAtDesc(UUID teacherId);
    List<Conversation> findByStudentIdInOrderByLastMessageAtDesc(List<UUID> studentIds);
    Optional<Conversation> findByStudentIdAndTeacherId(UUID studentId, UUID teacherId);
}
