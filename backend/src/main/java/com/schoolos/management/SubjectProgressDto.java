package com.schoolos.management;

import java.util.List;

public record SubjectProgressDto(
        int completed,
        int total,
        List<TopicDto> topics
) {}
