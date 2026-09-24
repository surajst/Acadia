package com.concept.tasks.app;
import com.concept.tasks.data.TeacherTaskRequest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Flat request payload for teacher task creation — a web-layer mirror of the
 * legacy management TeacherTaskRequest, with taskType bound as a String so the
 * web layer never references the management enum. The service maps it across
 * (ADR 0001).
 */
public class CreateTaskRequest {

    /**
     * XP a task is worth.
     *
     * <p>The form carried min="1" and the app's own New task screen stripped
     * the minus sign, so both clients looked safe -- and the API accepted
     * -10 anyway, which reached a child's Challenges list as "+-10 XP". A
     * browser honours a min attribute and an HTTP client ignores it, so the
     * rule has to live here.
     */
    @Min(value = 1, message = "A task has to be worth at least 1 XP.")
    @Max(value = MAX_XP_REWARD, message = "A task cannot be worth more than " + MAX_XP_REWARD + " XP.")
    private Integer xpReward;

    /** Above this, a typo (1000 for 100) reads as a mistake, not a reward. */
    public static final int MAX_XP_REWARD = 1000;

    @NotBlank(message = "Give the task a title.")
    private String title;
    private String description;
    private String subjectCode;
    private String taskType;
    private Integer standard;
    private Boolean assignedToClass;
    private UUID studentId;
    private LocalDate dueDate;
    private String question1;
    private String question2;
    private String question3;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public Integer getStandard() { return standard; }
    public void setStandard(Integer standard) { this.standard = standard; }
    public Boolean getAssignedToClass() { return assignedToClass; }
    public void setAssignedToClass(Boolean assignedToClass) { this.assignedToClass = assignedToClass; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getQuestion1() { return question1; }
    public void setQuestion1(String question1) { this.question1 = question1; }
    public String getQuestion2() { return question2; }
    public void setQuestion2(String question2) { this.question2 = question2; }
    public String getQuestion3() { return question3; }
    public void setQuestion3(String question3) { this.question3 = question3; }
}
