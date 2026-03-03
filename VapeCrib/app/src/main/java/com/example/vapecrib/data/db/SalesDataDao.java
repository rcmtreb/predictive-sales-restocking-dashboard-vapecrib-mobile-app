package com.example.vapecrib.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SalesDataDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<SalesDataEntity> items);

    /** All rows ordered by date — used for "All Time" view. */
    @Query("SELECT * FROM sales_data ORDER BY date ASC")
    List<SalesDataEntity> getAll();

    /**
     * Filter by date range (inclusive).
     * Dates are stored as "yyyy-MM-dd" strings so lexicographic comparison works correctly.
     */
    @Query("SELECT * FROM sales_data WHERE date >= :start AND date <= :end ORDER BY date ASC")
    List<SalesDataEntity> getByDateRange(String start, String end);

    @Query("SELECT COUNT(*) FROM sales_data")
    int count();

    @Query("DELETE FROM sales_data")
    void deleteAll();
}
