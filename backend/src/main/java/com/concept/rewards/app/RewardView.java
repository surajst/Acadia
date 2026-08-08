package com.concept.rewards.app;

import java.util.UUID;

/** Flat view of a reward for the admin inventory table (no entity leaks out). */
public record RewardView(UUID id, String displayEmoji, String title, String description,
                         int xpCost, int inventoryCount) {}
