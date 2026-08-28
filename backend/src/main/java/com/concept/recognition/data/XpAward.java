package com.concept.recognition.data;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One thing a teacher recognised a child for.
 *
 * <p>The reason is the point. A running total tells a parent nothing; "Helped
 * tidy up without being asked" is what gets read out at the dinner table, and
 * keeping the row means a parent can see what earned the number rather than
 * just watching it move.
 *
 * <p>The awarding teacher's name is denormalised alongside their id: staff
 * leave, and "awarded by Anita Deshpande" should still read correctly in a
 * child's history after her account is gone.
 */
@Entity
@Table(name = "xp_awards")
public class XpAward extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "awarded_by_user_id")
    private UUID awardedByUserId;

    @Column(name = "awarded_by_name")
    private String awardedByName;

    @Column(name = "badge_code", nullable = false, length = 64)
    private String badgeCode;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getAwardedByUserId() {
        return awardedByUserId;
    }

    public void setAwardedByUserId(UUID awardedByUserId) {
        this.awardedByUserId = awardedByUserId;
    }

    public String getAwardedByName() {
        return awardedByName;
    }

    public void setAwardedByName(String awardedByName) {
        this.awardedByName = awardedByName;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
