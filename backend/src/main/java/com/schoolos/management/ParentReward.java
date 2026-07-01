package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "parent_rewards")
public class ParentReward extends BaseTenantEntity {
    @Id
    private UUID id;
    private String rewardTitle;
    private Integer xpCost;
    private String status;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Parent parent;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Student student;
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getRewardTitle() { return rewardTitle; }
    public void setRewardTitle(String rewardTitle) { this.rewardTitle = rewardTitle; }
    public Integer getXpCost() { return xpCost; }
    public void setXpCost(Integer xpCost) { this.xpCost = xpCost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Parent getParent() { return parent; }
    public void setParent(Parent parent) { this.parent = parent; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
}
