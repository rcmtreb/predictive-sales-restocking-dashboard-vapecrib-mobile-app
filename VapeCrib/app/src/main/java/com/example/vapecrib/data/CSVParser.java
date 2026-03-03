package com.example.vapecrib.data;

import android.content.Context;
import com.example.vapecrib.data.db.InventoryItemEntity;
import com.example.vapecrib.data.db.SalesDataEntity;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to parse sales and inventory data from CSV files.
 * Can read from assets/ folder or from File objects (for import).
 */
public class CSVParser {

    /**
     * Parse sales_data.csv from assets
     */
    public static List<SalesDataEntity> parseSalesCSV(Context context) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("sales_data.csv")))) {
            return parseSalesData(reader);
        }
    }

    /**
     * Parse sales_data.csv from File (for imports)
     */
    public static List<SalesDataEntity> parseSalesCSV(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseSalesData(reader);
        }
    }

    /**
     * Parse inventory_data.csv from assets
     */
    public static List<InventoryItemEntity> parseInventoryCSV(Context context) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("inventory_data.csv")))) {
            return parseInventoryData(reader);
        }
    }

    /**
     * Parse inventory_data.csv from File (for imports)
     */
    public static List<InventoryItemEntity> parseInventoryCSV(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return parseInventoryData(reader);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private static List<SalesDataEntity> parseSalesData(BufferedReader reader) throws Exception {
        List<SalesDataEntity> data = new ArrayList<>();
        String line;
        boolean isHeader = true;

        while ((line = reader.readLine()) != null) {
            if (isHeader) {
                isHeader = false;
                continue; // Skip header
            }

            String[] fields = line.split(",");
            if (fields.length < 5) continue;

            try {
                String date = fields[0].trim();
                float actualSales = Float.parseFloat(fields[1].trim());
                float forecastedSales = Float.parseFloat(fields[2].trim());
                float revenue = Float.parseFloat(fields[3].trim());
                int productsCount = Integer.parseInt(fields[4].trim());

                SalesDataEntity entity = new SalesDataEntity(date, actualSales, forecastedSales, revenue, productsCount);
                data.add(entity);
            } catch (NumberFormatException e) {
                // Skip malformed rows
                continue;
            }
        }

        return data;
    }

    private static List<InventoryItemEntity> parseInventoryData(BufferedReader reader) throws Exception {
        List<InventoryItemEntity> data = new ArrayList<>();
        String line;
        boolean isHeader = true;

        while ((line = reader.readLine()) != null) {
            if (isHeader) {
                isHeader = false;
                continue; // Skip header
            }

            String[] fields = line.split(",");
            if (fields.length < 7) continue;

            try {
                String productName = fields[0].trim();
                int currentStock = Integer.parseInt(fields[1].trim());
                int minStockLevel = Integer.parseInt(fields[2].trim());
                int maxStockLevel = Integer.parseInt(fields[3].trim());
                String category = fields[4].trim();
                String stockLevel = fields[5].trim();
                boolean isExpiringSoon = Boolean.parseBoolean(fields[6].trim());

                InventoryItemEntity entity = new InventoryItemEntity();
                entity.productName = productName;
                entity.currentStock = currentStock;
                entity.minStockLevel = minStockLevel;
                entity.maxStockLevel = maxStockLevel;
                entity.category = category;
                entity.stockLevel = stockLevel;
                entity.isExpiringSoon = isExpiringSoon;

                data.add(entity);
            } catch (Exception e) {
                // Skip malformed rows
                continue;
            }
        }

        return data;
    }
}
