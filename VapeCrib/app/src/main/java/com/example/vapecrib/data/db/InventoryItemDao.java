package com.example.vapecrib.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface InventoryItemDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<InventoryItemEntity> items);

    @Query("SELECT * FROM inventory_items ORDER BY stockLevel, productName ASC")
    List<InventoryItemEntity> getAll();

    @Query("SELECT * FROM inventory_items WHERE stockLevel = :level ORDER BY productName ASC")
    List<InventoryItemEntity> getByStockLevel(String level);

    @Query("SELECT * FROM inventory_items WHERE isExpiringSoon = 1 ORDER BY productName ASC")
    List<InventoryItemEntity> getExpiringSoon();

    @Query("SELECT COUNT(*) FROM inventory_items WHERE stockLevel = :level")
    int countByLevel(String level);

    @Query("SELECT COUNT(*) FROM inventory_items WHERE isExpiringSoon = 1")
    int countExpiring();

    @Query("SELECT COUNT(*) FROM inventory_items")
    int count();

    @Query("DELETE FROM inventory_items")
    void deleteAll();
}
