package com.schoolos.academics;

import java.time.LocalDateTime;
import java.util.UUID;

public class SubmissionQueueDto {
    private UUID id;
    private String studentName;
    private String skillName;
    private int xpBounty;
    private LocalDateTime submittedAt;

    public SubmissionQueueDto(UUID id, String studentName, String skillName, int xpBounty, LocalDateTime submittedAt) {
        this.id = id;
        this.studentName = studentName;
        this.skillName = skillName;
        this.xpBounty = xpBounty;
        this.submittedAt = submittedAt;
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public String getStudentName() { return studentName; }
    public String getSkillName() { return skillName; }
    public int getXpBounty() { return xpBounty; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
