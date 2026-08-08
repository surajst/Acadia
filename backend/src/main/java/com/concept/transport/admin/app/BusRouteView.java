package com.concept.transport.admin.app;

import java.time.Instant;
import java.util.UUID;

/**
 * Flat view of a bus route for the admin transport console. Mirrors the fields
 * the admin UI reads (name, assigned driver, last-known location) — no entity
 * leaves the application layer.
 */
public record BusRouteView(UUID id, String name, UUID driverId,
                           Double currentLatitude, Double currentLongitude, Instant lastPingAt) {}
