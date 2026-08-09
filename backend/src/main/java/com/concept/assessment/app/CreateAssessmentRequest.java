package com.concept.assessment.app;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Flat request payload for creating an assessment — a web-layer mirror of the
 * academics CreateAssessmentRequest, with term bound as a String so the web
 * layer never references the academics enum. The service maps it (ADR 0001).
 */
public class CreateAssessmentRequest {
    private String title;
    private String subjectCode;
    private UUID classSectionId;
    private String term;
    private Integer maxScore;
    private LocalDate assessmentDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public UUID getClassSectionId() { return classSectionId; }
    public void setClassSectionId(UUID classSectionId) { this.classSectionId = classSectionId; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }
}
