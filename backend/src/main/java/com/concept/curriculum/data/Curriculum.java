package com.concept.curriculum.data;

import com.concept.common.BaseTenantEntity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "curriculums")
public class Curriculum extends BaseTenantEntity {

    @Id
    private UUID id;
    
    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "syllabus_type", nullable = false)
    private SyllabusType syllabusType;
    
    @Column(name = "standard", nullable = false)
    private Integer standard;
    
    @Column(name = "subject_type", nullable = false)
    private String subjectCode;

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

    public String getSubjectCode() {
        return subjectCode;
    }

    public void setSubjectCode(String subjectCode) {
        this.subjectCode = subjectCode;
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
