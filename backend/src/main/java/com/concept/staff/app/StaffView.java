package com.concept.staff.app;

import java.util.UUID;

/**
 * Flat view of a staff member for the admin staff list (no entity leaks out).
 *
 * <p>{@code active} and {@code approvalStatus} are both here because they are
 * different facts and the registry used to show the first under the second's
 * name: an account that had been deliberately left pending was rendered
 * "Active", and the admin had no way to see why the sign-in they had just
 * handed out did not work.
 */
public record StaffView(UUID id, String fullName, String email, String role,
                        boolean active, String approvalStatus) {}
