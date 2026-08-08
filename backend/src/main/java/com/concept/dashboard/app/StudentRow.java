package com.concept.dashboard.app;

import java.util.UUID;

/** Flat roster row for the unified dashboard (no entity reaches the template). */
public record StudentRow(UUID id, String rollNumber, String firstName, String lastName,
                         String gradeName, String sectionName) {}
