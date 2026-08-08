package com.concept.staff.app;

import java.util.UUID;

/** Flat view of a staff member for the admin staff list (no entity leaks out). */
public record StaffView(UUID id, String fullName, String email, String role, boolean active) {}
