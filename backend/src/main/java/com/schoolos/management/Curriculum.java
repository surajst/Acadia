package com.schoolos.management;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "curriculums")
public class Curriculum {

    @Id
    private UUID id;
    
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "syllabus_type", nullable = false)
    private SyllabusType syllabusType;
    
    @Column(name = "standard", nullable = false)
    private Integer standard;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "topic_order", nullable = false)
    private Integer topicOrder = 0;

    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward = 50;

    public Curriculum() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public void setAcademicYearId(UUID academicYearId) {
        this.academicYearId = academicYearId;
    }

    public SyllabusType getSyllabusType() {
        return syllabusType;
    }

    public void setSyllabusType(SyllabusType syllabusType) {
        this.syllabusType = syllabusType;
    }

    public Integer getStandard() {
        return standard;
    }

    public void setStandard(Integer standard) {
        this.standard = standard;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public Integer getTopicOrder() {
        return topicOrder;
    }

    public void setTopicOrder(Integer topicOrder) {
        this.topicOrder = topicOrder;
    }

    public Integer getXpReward() {
        return xpReward;
    }

    public void setXpReward(Integer xpReward) {
        this.xpReward = xpReward;
    }
}
