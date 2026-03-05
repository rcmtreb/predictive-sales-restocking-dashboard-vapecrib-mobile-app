package com.example.vapecrib.data;

import android.annotation.SuppressLint;
import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.data.db.AppDatabase;
import com.example.vapecrib.data.db.InventoryItemDao;
import com.example.vapecrib.data.db.InventoryItemEntity;
import com.example.vapecrib.data.db.SalesDataDao;
import com.example.vapecrib.data.db.SalesDataEntity;
import com.example.vapecrib.model.InventoryItem;
import com.example.vapecrib.model.SalesData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for all app data.
 *
 * Reads from Room (SQLite) on a background thread and delivers results
 * via MutableLiveData so ViewModels can observe them.
 *
 * To swap in a real backend later:
 *   1. Fetch data from your API in a background thread.
 *   2. Insert into the same Room DAOs.
 *   3. The ViewModels don't need to change at all.
 */
public class AppRepository {

    private final SalesDataDao    salesDao;
    private final InventoryItemDao inventoryDao;

    public AppRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        salesDao     = db.salesDataDao();
        inventoryDao = db.inventoryItemDao();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sales data
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load all sales records and post them to the returned LiveData.
     * Caller should observe this on the main thread.
     */
    public LiveData<List<SalesData>> loadAllSalesData() {
        MutableLiveData<List<SalesData>> result = new MutableLiveData<>();
        AppDatabase.dbExecutor.execute(() -> {
            List<SalesDataEntity> entities = salesDao.getAll();
            result.postValue(mapSalesEntities(entities));
        });
        return result;
    }

    /**
     * Load sales records filtered by date range (inclusive).
     */
    public LiveData<List<SalesData>> loadSalesDataByRange(LocalDate start, LocalDate end) {
        MutableLiveData<List<SalesData>> result = new MutableLiveData<>();
        AppDatabase.dbExecutor.execute(() -> {
            List<SalesDataEntity> entities =
                    salesDao.getByDateRange(start.toString(), end.toString());
            result.postValue(mapSalesEntities(entities));
        });
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inventory data
    // ─────────────────────────────────────────────────────────────────────────

    /** Load all inventory items. */
    public LiveData<List<InventoryItem>> loadAllInventory() {
        MutableLiveData<List<InventoryItem>> result = new MutableLiveData<>();
        AppDatabase.dbExecutor.execute(() -> {
            List<InventoryItemEntity> entities = inventoryDao.getAll();
            result.postValue(mapInventoryEntities(entities));
        });
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Counts (used by Dashboard / Reports KPI tiles)
    // ─────────────────────────────────────────────────────────────────────────

    public LiveData<Integer> loadCriticalCount() {
        return loadCount("CRITICAL");
    }

    public LiveData<Integer> loadHighCount() {
        return loadCount("HIGH");
    }

    public LiveData<Integer> loadMediumCount() {
        return loadCount("MEDIUM");
    }

    public LiveData<Integer> loadLowCount() {
        return loadCount("LOW");
    }

    public LiveData<Integer> loadExpiringCount() {
        MutableLiveData<Integer> result = new MutableLiveData<>();
        AppDatabase.dbExecutor.execute(() ->
                result.postValue(inventoryDao.countExpiring()));
        return result;
    }

    private LiveData<Integer> loadCount(String level) {
        MutableLiveData<Integer> result = new MutableLiveData<>();
        AppDatabase.dbExecutor.execute(() ->
                result.postValue(inventoryDao.countByLevel(level)));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers — entity → model
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("NewApi")
    private List<SalesData> mapSalesEntities(List<SalesDataEntity> entities) {
        List<SalesData> list = new ArrayList<>();
        for (SalesDataEntity e : entities) {
            list.add(new SalesData(
                    LocalDate.parse(e.date),
                    e.actualSales,
                    e.forecastedSales,
                    e.revenue,
                    e.productsCount
            ));
        }
        return list;
    }

    private List<InventoryItem> mapInventoryEntities(List<InventoryItemEntity> entities) {
        List<InventoryItem> list = new ArrayList<>();
        for (InventoryItemEntity e : entities) {
            InventoryItem.StockLevel level;
            try {
                level = InventoryItem.StockLevel.valueOf(e.stockLevel);
            } catch (IllegalArgumentException ex) {
                level = InventoryItem.StockLevel.LOW;
            }
            list.add(new InventoryItem(
                    e.productName,
                    e.currentStock,
                    e.minStockLevel,
                    e.maxStockLevel,
                    e.category,
                    level,
                    e.isExpiringSoon
            ));
        }
        return list;
    }
}
