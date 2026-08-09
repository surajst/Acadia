package com.concept.curriculum.data;

import java.time.LocalDateTime;
import java.util.UUID;

public record TopicDto(
        UUID id,
        String topicName,
        String subjectType,
        int standard,
        int xpReward,
        int topicOrder,
        boolean completed,
        LocalDateTime completedAt,
        String status,
        String rejectionReason
) {}
