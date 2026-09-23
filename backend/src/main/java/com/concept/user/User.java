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
     * every pre-existing account.
     *
     * <p>PENDING gates login until a PRINCIPAL or ADMIN decides (see
     * CustomUserDetailsService and OversightService#decideStaff). Nothing an
     * authenticated admin creates lands PENDING: the admin console and the
     * roster importer both run as one of the two approving roles, so leaving
     * their accounts pending meant waiting on a decision that had already been
     * made — and, in a school with no principal appointed, on nobody.
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

    /**
     * Stores the address in its canonical form.
     *
     * <p>An email address's domain is case-insensitive and every mailbox this
     * system issues is lowercase, so "Suraj10@gmail.com" and
     * "suraj10@gmail.com" are one account. They were two: the column is
     * unique and the lookup was by exact match, so registering in one case and
     * signing in in another produced "Invalid username or password" against an
     * account that plainly existed. Normalising here — rather than at each of
     * the thirty-odd lookups — means no future write path can reintroduce it.
     *
     * <p>Hibernate uses field access on this entity (the mapping annotations
     * are on the fields), so loading a row does not pass through this setter
     * and cannot rewrite what is already stored.
     */
    public void setEmail(String email) { this.email = normaliseEmail(email); }

    /**
     * The canonical form of an address, for the lookup side. Null in, null
     * out: a missing username is a distinct case from an empty one, and the
     * callers that pass {@code authentication.getName()} on an anonymous
     * request depend on it staying null rather than becoming "".
     */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

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
