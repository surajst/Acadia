package com.concept.user;

import com.concept.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User extends BaseTenantEntity {

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * Nullable — null is treated as APPROVED for backward compatibility with
     * every pre-existing account. Only new staff (TEACHER/ADMIN/PRINCIPAL)
     * invites are set to PENDING, gating login until a PRINCIPAL/ADMIN
     * approves them (see CustomUserDetailsService).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    private ApprovalStatus approvalStatus;

    /**
     * When this user's photograph was last set, or null when they have none.
     *
     * <p>The bytes are in {@code user_photos}, not here: a User is loaded on
     * nearly every authenticated request, and a byte[] on this entity would be
     * fetched with all of them. This column is what lets the profile payload
     * report that a photo exists, and cache-bust its URL, without reading it.
     */
    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }

    public Instant getPhotoUpdatedAt() { return photoUpdatedAt; }
    public void setPhotoUpdatedAt(Instant photoUpdatedAt) { this.photoUpdatedAt = photoUpdatedAt; }
}
