package com.concept.parent.app;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Flat request payload for the mobile "assign a quest to my child" call. Lives
 * in the application layer so both the web controller (binds it) and the
 * service (consumes it) can share it without the app layer depending on web.
 */
public class AssignQuestRequest {

    /** A quest a parent sets is worth XP; the same bounds as a teacher task. */
    public static final int MAX_XP_REWARD = 1000;

    @NotBlank(message = "Give the quest a title.")
    private String title;
    private String description;

    @Min(value = 1, message = "A quest has to be worth at least 1 XP.")
    @Max(value = MAX_XP_REWARD, message = "A quest cannot be worth more than " + MAX_XP_REWARD + " XP.")
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
