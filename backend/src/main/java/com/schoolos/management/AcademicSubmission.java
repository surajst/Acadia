package com.schoolos.management;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "academic_submissions")
public class AcademicSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID studentId;

    @Column(nullable = false)
    private String skillName;

    @Column(nullable = false)
    private Integer xpBounty;

    @Column(name = "status")
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED
    
    @Column(name = "rejection_reason")
    private String rejectionReason;

    private LocalDateTime submittedAt;

    @Column(length = 2000)
    private String proofOfWorkNotes;

    @Column(name = "answer_1", length = 1000)
    private String answer1;


    @Column(name = "answer_2", length = 1000)
    private String answer2;

    @Column(name = "answer_3", length = 1000)
    private String answer3;

    @Column(name = "teacher_task_id")
    private UUID teacherTaskId;

    // Constructors
    public AcademicSubmission() {}

    public AcademicSubmission(UUID studentId, String skillName, Integer xpBounty) {
        this.studentId = studentId;
        this.skillName = skillName;
        this.xpBounty = xpBounty;
        this.status = "PENDING";
        this.submittedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public Integer getXpBounty() { return xpBounty; }
    public void setXpBounty(Integer xpBounty) { this.xpBounty = xpBounty; }
    public String getStatus() { return status; }
    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getProofOfWorkNotes() { return proofOfWorkNotes; }
    public void setProofOfWorkNotes(String proofOfWorkNotes) { this.proofOfWorkNotes = proofOfWorkNotes; }
    
    public String getAnswer1() { return answer1; }
    public void setAnswer1(String answer1) { this.answer1 = answer1; }
    
    public String getAnswer2() { return answer2; }
    public void setAnswer2(String answer2) { this.answer2 = answer2; }
    
    public String getAnswer3() { return answer3; }
    public void setAnswer3(String answer3) { this.answer3 = answer3; }
    
    public UUID getTeacherTaskId() { return teacherTaskId; }
    public void setTeacherTaskId(UUID teacherTaskId) { this.teacherTaskId = teacherTaskId; }
}
