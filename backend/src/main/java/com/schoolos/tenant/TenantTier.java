package com.schoolos.tenant;

/**
 * FULL_SMS — full school management system (fees, curriculum admin, etc).
 * PARENT_APP_ONLY — tenant provisioned for just the parent-engagement app
 * (Users, Students, ClassSections, ParentQuest/reward/performance data);
 * admin/fee/curriculum-management surfaces are gated off.
 */
public enum TenantTier {
    FULL_SMS, PARENT_APP_ONLY
}
