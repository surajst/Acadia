package com.schoolos.management;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "teacher_tasks")
public class TeacherTask {

    @Id
    private UUID id;

    // Nullable (unlike BaseTenantEntity's tenant_id/academic_year_id, which are
    // NOT NULL) so the ALTER TABLE that adds these columns succeeds against
    // this entity's pre-existing rows; backfilled once via UserAccountLinkageSeeder.
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType taskType;

    @Column(nullable = false)
    private Integer standard;

    @Column(name = "assigned_to_class", nullable = false)
    private Boolean assignedToClass = true;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "created_by_teacher_id", nullable = false)
    private UUID createdByTeacherId;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 50;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "task_status", nullable = false)
    private String taskStatus = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "question_1", length = 500)
    private String question1;

    @Column(name = "question_2", length = 500)
    private String question2;

    @Column(name = "question_3", length = 500)
    private String question3;

    public TeacherTask() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAcademicYearId() { return academicYearId; }
    public void setAcademicYearId(UUID academicYearId) { this.academicYearId = academicYearId; }

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
    
    public UUID getCreatedByTeacherId() { return createdByTeacherId; }
    public void setCreatedByTeacherId(UUID createdByTeacherId) { this.createdByTeacherId = createdByTeacherId; }
    
    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
    
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public String getQuestion1() { return question1; }
    public void setQuestion1(String question1) { this.question1 = question1; }
    
    public String getQuestion2() { return question2; }
    public void setQuestion2(String question2) { this.question2 = question2; }
    
    public String getQuestion3() { return question3; }
    public void setQuestion3(String question3) { this.question3 = question3; }
}
