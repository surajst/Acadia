package com.schoolos.academics;

import com.schoolos.management.Student;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_assessment_scores")
public class StudentAssessmentScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "graded_by_teacher_id", nullable = false)
    private UUID gradedByTeacherId;

    @Column(name = "graded_at", nullable = false)
    private LocalDateTime gradedAt = LocalDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public UUID getGradedByTeacherId() { return gradedByTeacherId; }
    public void setGradedByTeacherId(UUID gradedByTeacherId) { this.gradedByTeacherId = gradedByTeacherId; }

    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
}
