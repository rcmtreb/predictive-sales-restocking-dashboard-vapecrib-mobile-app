package com.example.vapecrib.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity — one row per day of sales data.
 * Pre-populated from SampleDataProvider on first launch.
 * Replace the Callback in AppDatabase to load from a real API instead.
 */
@Entity(tableName = "sales_data")
public class SalesDataEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Stored as "yyyy-MM-dd" string (via DateConverter). */
    public String date;

    public float actualSales;
    public float forecastedSales;
    public float revenue;
    public int   productsCount;

    public SalesDataEntity() {}

    public SalesDataEntity(String date, float actualSales, float forecastedSales,
                            float revenue, int productsCount) {
        this.date            = date;
        this.actualSales     = actualSales;
        this.forecastedSales = forecastedSales;
        this.revenue         = revenue;
        this.productsCount   = productsCount;
    }
}
