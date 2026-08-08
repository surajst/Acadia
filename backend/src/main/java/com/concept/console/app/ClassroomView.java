package com.concept.console.app;

import java.util.UUID;

/**
 * Flat view of a classroom for the admin console hub. Carries a pre-computed
 * {@code studentCount} so the template never touches a lazy entity collection.
 */
public record ClassroomView(UUID id, String gradeLevel, String sectionName, String roomNumber,
                            long studentCount, int totalCapacity) {}
