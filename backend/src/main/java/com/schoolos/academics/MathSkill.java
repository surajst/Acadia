package com.schoolos.academics;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "math_skills")
public class MathSkill extends BaseTenantEntity {
    @Id
    private UUID id;
    private String skillName;
    private Integer maxXpReward;

    @ManyToOne
    @JoinColumn(name = "chapter_id", nullable = false)
    private MathChapter chapter;
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public Integer getMaxXpReward() { return maxXpReward; }
    public void setMaxXpReward(Integer maxXpReward) { this.maxXpReward = maxXpReward; }
    public MathChapter getChapter() { return chapter; }
    public void setChapter(MathChapter chapter) { this.chapter = chapter; }
}
