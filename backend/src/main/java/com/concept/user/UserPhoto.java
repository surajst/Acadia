package com.concept.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's profile photograph, in a table of its own.
 *
 * <p>Deliberately not a column on {@link User}. A User is loaded on nearly
 * every authenticated request, and a {@code byte[]} on that entity is fetched
 * eagerly with all of them — {@code @Basic(fetch = LAZY)} does nothing for a
 * basic attribute without bytecode enhancement, which this project does not
 * enable. Keeping the bytes here means only the endpoint that serves them ever
 * reads them.
 *
 * <p>Does not extend BaseTenantEntity: that would require an academic year,
 * and a photograph does not belong to one — it outlives the year the user
 * happened to upload it in. The tenant is carried explicitly instead, because
 * serving a photo still has to be scoped to the caller's own school.
 */
@Entity
@Table(name = "user_photos")
public class UserPhoto {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public byte[] getBytes() { return bytes; }
    public void setBytes(byte[] bytes) { this.bytes = bytes; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
