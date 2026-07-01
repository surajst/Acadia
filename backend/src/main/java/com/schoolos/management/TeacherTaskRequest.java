package com.schoolos.management;

import java.time.LocalDate;
import java.util.UUID;

public class TeacherTaskRequest {
    private String title;
    private String description;
    private SubjectType subjectType;
    private TaskType taskType;
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

    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }

    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }

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
