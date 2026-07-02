package com.schoolos.academics;

import com.schoolos.common.BaseTenantEntity;
import com.schoolos.management.ClassSection;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assessments")
public class Assessment extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "subject_type", nullable = false)
    private String subjectCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_section_id", nullable = false)
    private ClassSection classSection;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentTerm term;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "created_by_teacher_id", nullable = false)
    private UUID createdByTeacherId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public ClassSection getClassSection() { return classSection; }
    public void setClassSection(ClassSection classSection) { this.classSection = classSection; }

    public AssessmentTerm getTerm() { return term; }
    public void setTerm(AssessmentTerm term) { this.term = term; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }

    public UUID getCreatedByTeacherId() { return createdByTeacherId; }
    public void setCreatedByTeacherId(UUID createdByTeacherId) { this.createdByTeacherId = createdByTeacherId; }
}
