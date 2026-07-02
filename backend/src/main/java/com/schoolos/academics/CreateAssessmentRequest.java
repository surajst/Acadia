package com.schoolos.academics;

import java.time.LocalDate;
import java.util.UUID;

public class CreateAssessmentRequest {
    private String title;
    private String subjectCode;
    private UUID classSectionId;
    private AssessmentTerm term;
    private Integer maxScore;
    private LocalDate assessmentDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public UUID getClassSectionId() { return classSectionId; }
    public void setClassSectionId(UUID classSectionId) { this.classSectionId = classSectionId; }

    public AssessmentTerm getTerm() { return term; }
    public void setTerm(AssessmentTerm term) { this.term = term; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }
}
