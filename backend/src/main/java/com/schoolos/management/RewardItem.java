package com.schoolos.management;

import com.schoolos.common.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "reward_items")
public class RewardItem extends BaseTenantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "xp_cost", nullable = false)
    private int xpCost;

    @Column(name = "display_emoji")
    private String displayEmoji;

    @Column(name = "inventory_count", nullable = false)
    private int inventoryCount;

    // Constructors
    public RewardItem() {}

    public RewardItem(UUID id, String title, String description, int xpCost, String displayEmoji, int inventoryCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.xpCost = xpCost;
        this.displayEmoji = displayEmoji;
        this.inventoryCount = inventoryCount;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getXpCost() {
        return xpCost;
    }

    public void setXpCost(int xpCost) {
        this.xpCost = xpCost;
    }

    public String getDisplayEmoji() {
        return displayEmoji;
    }

    public void setDisplayEmoji(String displayEmoji) {
        this.displayEmoji = displayEmoji;
    }

    public int getInventoryCount() {
        return inventoryCount;
    }

    public void setInventoryCount(int inventoryCount) {
        this.inventoryCount = inventoryCount;
    }
}
