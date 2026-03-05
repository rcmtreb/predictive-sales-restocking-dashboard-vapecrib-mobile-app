package com.example.vapecrib.data;

import android.annotation.SuppressLint;
import android.content.Context;
import com.example.vapecrib.data.db.AppDatabase;
import com.example.vapecrib.data.db.InventoryItemEntity;
import com.example.vapecrib.data.db.SalesDataEntity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility class to export sales and inventory data to CSV files.
 */
public class CSVExporter {

    /**
     * Export all sales data to CSV file in Downloads folder
     */
    @SuppressLint("NewApi")
    public static String exportSalesCSV(Context context) throws Exception {
        List<SalesDataEntity> salesData = AppDatabase.getInstance(context)
                .salesDataDao()
                .getAll();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "sales_export_" + timestamp + ".csv";
        File downloadsDir = new File(context.getExternalFilesDir(null), "exports");
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File csvFile = new File(downloadsDir, fileName);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(csvFile))) {

            // Header
            writer.write("date,actualSales,forecastedSales,revenue,productsCount\n");

            // Data
            for (SalesDataEntity entity : salesData) {
                writer.write(String.format("%s,%.2f,%.2f,%.2f,%d\n",
                        entity.date,
                        entity.actualSales,
                        entity.forecastedSales,
                        entity.revenue,
                        entity.productsCount));
            }
        }

        return csvFile.getAbsolutePath();
    }

    /**
     * Export all inventory data to CSV file in Downloads folder
     */
    public static String exportInventoryCSV(Context context) throws Exception {
        List<InventoryItemEntity> inventoryData = AppDatabase.getInstance(context)
                .inventoryItemDao()
                .getAll();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "inventory_export_" + timestamp + ".csv";
        File downloadsDir = new File(context.getExternalFilesDir(null), "exports");
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File csvFile = new File(downloadsDir, fileName);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(csvFile))) {

            // Header
            writer.write("productName,currentStock,minStockLevel,maxStockLevel,category,stockLevel,isExpiringSoon\n");

            // Data
            for (InventoryItemEntity entity : inventoryData) {
                writer.write(String.format("%s,%d,%d,%d,%s,%s,%b\n",
                        entity.productName,
                        entity.currentStock,
                        entity.minStockLevel,
                        entity.maxStockLevel,
                        entity.category,
                        entity.stockLevel,
                        entity.isExpiringSoon));
            }
        }

        return csvFile.getAbsolutePath();
    }

    /**
     * Import sales data from CSV file
     */
    public static int importSalesCSV(Context context, File csvFile) throws Exception {
        List<SalesDataEntity> salesData = CSVParser.parseSalesCSV(csvFile);
        
        AppDatabase.getInstance(context)
                .salesDataDao()
                .insertAll(salesData);
        
        return salesData.size();
    }

    /**
     * Import inventory data from CSV file
     */
    public static int importInventoryCSV(Context context, File csvFile) throws Exception {
        List<InventoryItemEntity> inventoryData = CSVParser.parseInventoryCSV(csvFile);
        
        AppDatabase.getInstance(context)
                .inventoryItemDao()
                .insertAll(inventoryData);
        
        return inventoryData.size();
    }
}
