package com.concept.console.app;

import java.util.UUID;

/**
 * Flat view of a class for the admin console hub. Carries a pre-computed
 * {@code studentCount} so the template never touches a lazy entity collection.
 *
 * <p>{@code gradeName} matches the column it comes from. It used to be called
 * gradeLevel while being fed from gradeName, which is the sort of mismatch that
 * made two structures for one idea survive as long as they did.
 */
public record ClassroomView(UUID id, String gradeName, String sectionName, String roomNumber,
                            long studentCount, int totalCapacity) {}
