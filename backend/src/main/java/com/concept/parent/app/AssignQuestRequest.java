package com.concept.parent.app;

import java.util.UUID;

/**
 * Flat request payload for the mobile "assign a quest to my child" call. Lives
 * in the application layer so both the web controller (binds it) and the
 * service (consumes it) can share it without the app layer depending on web.
 */
public class AssignQuestRequest {
    private String title;
    private String description;
    private Integer xpReward;
    private UUID studentId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
}
