package com.concept.roster.app;

import java.util.UUID;

/** Flat view of a class section for the admin class-structure list. */
public record ClassSectionDto(UUID id, String gradeName, String sectionName, String roomNumber) {}
