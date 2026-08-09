package com.concept.assignment.data;
import com.concept.curriculum.data.TopicDto;

import java.util.List;

public record SubjectProgressDto(
        int completed,
        int total,
        List<TopicDto> topics
) {}
