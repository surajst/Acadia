package com.schoolos.academics;

import com.schoolos.management.SubjectType;

import java.time.LocalDate;
import java.util.UUID;

public class CreateAssessmentRequest {
    private String title;
    private SubjectType subjectType;
    private UUID classSectionId;
    private AssessmentTerm term;
    private Integer maxScore;
    private LocalDate assessmentDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }

    public UUID getClassSectionId() { return classSectionId; }
    public void setClassSectionId(UUID classSectionId) { this.classSectionId = classSectionId; }

    public AssessmentTerm getTerm() { return term; }
    public void setTerm(AssessmentTerm term) { this.term = term; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }
}
