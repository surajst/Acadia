package com.concept.dashboard.app;

import java.time.LocalDateTime;
import java.util.UUID;

/** Flat syllabus-progress row for the teacher verification queue. */
public record TeacherProgressRow(UUID id, String subjectName, String topicName,
                                 String studentName, LocalDateTime submittedAt) {}
