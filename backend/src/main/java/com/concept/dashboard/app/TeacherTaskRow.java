package com.concept.dashboard.app;

import java.time.LocalDateTime;
import java.util.UUID;

/** Flat milestone-submission row for the teacher verification queue. */
public record TeacherTaskRow(UUID id, String skillName, Integer xpBounty, String studentName,
                             LocalDateTime submittedAt, String proofOfWorkNotes,
                             String answer1, String answer2, String answer3) {}
