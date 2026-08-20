package com.concept.staff.app;

import java.util.UUID;

/**
 * Outcome of inviting a staff member.
 *
 * <p>emailed is carried separately from the account id because the two can
 * disagree: the account is created first, and a failed send must not roll it
 * back or be swallowed. The admin needs to know which of the two happened, and
 * still needs the password when the email did not go.
 */
public record StaffInvite(UUID id, String temporaryPassword, boolean emailed, String emailDetail) {}
