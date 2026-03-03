package com.example.vapecrib.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.vapecrib.data.CSVParser;
import com.example.vapecrib.data.SampleDataProvider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main Room database.
 *
 * On first launch (onCreate) the database is seeded with sample data from CSV files.
 * CSV files are stored in assets/: sales_data.csv, inventory_data.csv
 */
@Database(
    entities = {SalesDataEntity.class, InventoryItemEntity.class},
    version  = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract SalesDataDao    salesDataDao();
    public abstract InventoryItemDao inventoryItemDao();

    // ---------- Singleton ----------
    private static volatile AppDatabase INSTANCE;
    private static Context appContext;

    public static final ExecutorService dbExecutor =
            Executors.newFixedThreadPool(2);

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            appContext = context.getApplicationContext();
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "vapecrib_db")
                            .addCallback(seedCallback)
                            .build();
                    
                    // Check if database is empty and load CSV if needed
                    dbExecutor.execute(() -> {
                        if (INSTANCE.salesDataDao().count() == 0) {
                            android.util.Log.d("AppDatabase", "Database is empty on startup - loading from CSV");
                            loadDataFromCSV(INSTANCE);
                        } else {
                            android.util.Log.d("AppDatabase", "Database already has data - skipping CSV load");
                        }
                    });
                }
            }
        }
        return INSTANCE;
    }

    // ---------- Seed callback — runs once when the DB is first created ----------
    private static final RoomDatabase.Callback seedCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            dbExecutor.execute(() -> {
                AppDatabase database = INSTANCE;
                if (database == null || appContext == null) return;

                android.util.Log.d("AppDatabase", "Database onCreate triggered - loading CSV data");
                loadDataFromCSV(database);
            });
        }
    };

    /**
     * Force reload all data from CSV files (clears and rebuilds database)
     */
    public static void reloadFromCSV(Context context) {
        if (INSTANCE == null) return;
        
        dbExecutor.execute(() -> {
            try {
                android.util.Log.d("AppDatabase", "Starting manual CSV reload");
                
                // Clear existing data
                INSTANCE.salesDataDao().deleteAll();
                INSTANCE.inventoryItemDao().deleteAll();
                
                android.util.Log.d("AppDatabase", "Cleared existing data");
                
                // Load from CSV
                loadDataFromCSV(INSTANCE);
                
                android.util.Log.d("AppDatabase", "CSV reload completed successfully");
            } catch (Exception e) {
                android.util.Log.e("AppDatabase", "Failed to reload CSV", e);
            }
        });
    }

    private static void loadDataFromCSV(AppDatabase database) {
        if (appContext == null) {
            android.util.Log.e("AppDatabase", "appContext is null, cannot load CSV");
            return;
        }

        try {
            SalesDataDao salesDao = database.salesDataDao();
            InventoryItemDao inventoryDao = database.inventoryItemDao();

            // ── Load Sales data from CSV ──────────────────────────
            android.util.Log.d("AppDatabase", "Loading sales data from CSV");
            java.util.List<com.example.vapecrib.data.db.SalesDataEntity> salesEntities = 
                com.example.vapecrib.data.CSVParser.parseSalesCSV(appContext);
            
            if (!salesEntities.isEmpty()) {
                salesDao.insertAll(salesEntities);
                android.util.Log.d("AppDatabase", "Inserted " + salesEntities.size() + " sales records");
            } else {
                android.util.Log.w("AppDatabase", "No sales data loaded from CSV");
            }

            // ── Load Inventory data from CSV ──────────────────────
            android.util.Log.d("AppDatabase", "Loading inventory data from CSV");
            java.util.List<com.example.vapecrib.data.db.InventoryItemEntity> inventoryEntities = 
                com.example.vapecrib.data.CSVParser.parseInventoryCSV(appContext);
            
            if (!inventoryEntities.isEmpty()) {
                inventoryDao.insertAll(inventoryEntities);
                android.util.Log.d("AppDatabase", "Inserted " + inventoryEntities.size() + " inventory items");
            } else {
                android.util.Log.w("AppDatabase", "No inventory data loaded from CSV");
            }
        } catch (Exception e) {
            android.util.Log.e("AppDatabase", "CSV parsing failed, using generated data", e);
            loadGeneratedData(database.salesDataDao(), database.inventoryItemDao());
        }
    }

    private static void loadGeneratedData(SalesDataDao salesDao, InventoryItemDao inventoryDao) {
        try {
            // Fallback: generate in-memory and insert
            List<com.example.vapecrib.model.SalesData> sampleSales = 
                SampleDataProvider.generateSampleSalesData();
            List<SalesDataEntity> salesEntities = new java.util.ArrayList<>();
            for (com.example.vapecrib.model.SalesData s : sampleSales) {
                salesEntities.add(new SalesDataEntity(
                    s.getDate().toString(),
                    s.getActualSales(),
                    s.getForecastedSales(),
                    s.getRevenue(),
                    s.getProductsCount()
                ));
            }
            salesDao.insertAll(salesEntities);

            List<com.example.vapecrib.model.InventoryItem> sampleInventory = 
                SampleDataProvider.generateSampleInventoryData();
            List<InventoryItemEntity> inventoryEntities = new java.util.ArrayList<>();
            for (com.example.vapecrib.model.InventoryItem item : sampleInventory) {
                inventoryEntities.add(new InventoryItemEntity(
                    item.getProductName(),
                    item.getCurrentStock(),
                    item.getMinStockLevel(),
                    item.getMaxStockLevel(),
                    item.getCategory(),
                    item.getStockLevel().name(),
                    item.isExpiringSoon()
                ));
            }
            inventoryDao.insertAll(inventoryEntities);
        } catch (Exception e) {
            android.util.Log.e("AppDatabase", "Failed to load generated data", e);
        }
    }
}
