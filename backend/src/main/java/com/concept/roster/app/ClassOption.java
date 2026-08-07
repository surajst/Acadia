package com.concept.roster.app;

import java.util.UUID;

/** A selectable classroom for the "edit student" dropdown — id plus a display label. */
public record ClassOption(UUID id, String label) {}
