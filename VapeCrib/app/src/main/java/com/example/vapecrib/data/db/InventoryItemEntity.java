package com.example.vapecrib.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity — one row per inventory product.
 * Pre-populated from SampleDataProvider on first launch.
 */
@Entity(tableName = "inventory_items")
public class InventoryItemEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String productName;
    public int    currentStock;
    public int    minStockLevel;
    public int    maxStockLevel;
    public String category;

    /** Stored as enum name: "CRITICAL", "HIGH", "MEDIUM", "LOW". */
    public String stockLevel;

    public boolean isExpiringSoon;

    public InventoryItemEntity() {}

    public InventoryItemEntity(String productName, int currentStock,
                                int minStockLevel, int maxStockLevel,
                                String category, String stockLevel, boolean isExpiringSoon) {
        this.productName    = productName;
        this.currentStock   = currentStock;
        this.minStockLevel  = minStockLevel;
        this.maxStockLevel  = maxStockLevel;
        this.category       = category;
        this.stockLevel     = stockLevel;
        this.isExpiringSoon = isExpiringSoon;
    }
}
