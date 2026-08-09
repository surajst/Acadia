package com.concept.tasks.app;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Flat request payload for teacher task creation — a web-layer mirror of the
 * legacy management TeacherTaskRequest, with taskType bound as a String so the
 * web layer never references the management enum. The service maps it across
 * (ADR 0001).
 */
public class CreateTaskRequest {
    private String title;
    private String description;
    private String subjectCode;
    private String taskType;
    private Integer standard;
    private Boolean assignedToClass;
    private UUID studentId;
    private Integer xpReward;
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
