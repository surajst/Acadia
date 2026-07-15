package com.schoolos.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String subdomain;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // Nullable so ALTER TABLE succeeds against existing rows without a migration
    // backfill step; a null tier is treated as FULL_SMS everywhere it's read
    // (see getEffectiveTier()).
    @Enumerated(EnumType.STRING)
    private TenantTier tier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Nullable so existing tenants (pre-dating this flag) aren't unexpectedly
    // routed into the setup wizard — null is treated as "already onboarded"
    // (see getEffectiveOnboardingCompleted()). New tenants set this false
    // explicitly at creation.
    @Column(name = "onboarding_completed")
    private Boolean onboardingCompleted;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubdomain() { return subdomain; }
    public void setSubdomain(String subdomain) { this.subdomain = subdomain; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public TenantTier getTier() { return tier; }
    public void setTier(TenantTier tier) { this.tier = tier; }

    public TenantTier getEffectiveTier() { return tier != null ? tier : TenantTier.FULL_SMS; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Boolean getOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(Boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }

    public boolean getEffectiveOnboardingCompleted() {
        return onboardingCompleted == null || onboardingCompleted;
    }
}
