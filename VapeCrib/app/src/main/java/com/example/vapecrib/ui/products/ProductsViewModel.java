package com.example.vapecrib.ui.products;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.vapecrib.data.SampleDataProvider;
import com.example.vapecrib.model.InventoryItem;

import java.util.ArrayList;
import java.util.List;

public class ProductsViewModel extends ViewModel {

    private final MutableLiveData<List<InventoryItem>> filteredInventory = new MutableLiveData<>();
    private final MutableLiveData<String> itemCountText = new MutableLiveData<>("0 items");

    private List<InventoryItem> allInventory;
    private String currentSearchQuery = "";
    private String currentStockFilter = "All";

    public static final String[] STOCK_FILTER_OPTIONS =
            {"All", "Critical", "High", "Medium", "Low"};

    public ProductsViewModel() {
        // Data is seeded once into Room on first launch; loaded fresh each ViewModel init
        allInventory = SampleDataProvider.generateSampleInventoryData();
        applyFilters();
    }

    public void setSearchQuery(String query) {
        currentSearchQuery = (query == null) ? "" : query.trim();
        applyFilters();
    }

    public void setStockLevelFilter(String level) {
        currentStockFilter = (level == null) ? "All" : level;
        applyFilters();
    }

    private void applyFilters() {
        List<InventoryItem> result = new ArrayList<>();
        String lowerQuery = currentSearchQuery.toLowerCase();

        for (InventoryItem item : allInventory) {
            // Stock level filter
            if (!currentStockFilter.equals("All")) {
                if (!item.getStockLevel().getDisplayName().equalsIgnoreCase(currentStockFilter)) {
                    continue;
                }
            }
            // Name / category search
            if (!lowerQuery.isEmpty()) {
                boolean nameMatch     = item.getProductName().toLowerCase().contains(lowerQuery);
                boolean categoryMatch = item.getCategory().toLowerCase().contains(lowerQuery);
                if (!nameMatch && !categoryMatch) continue;
            }
            result.add(item);
        }

        filteredInventory.setValue(result);
        int count = result.size();
        itemCountText.setValue(count + " item" + (count != 1 ? "s" : ""));
    }

    public LiveData<List<InventoryItem>> getFilteredInventory() { return filteredInventory; }
    public LiveData<String>              getItemCountText()      { return itemCountText;      }

    public void refreshData() {
        allInventory = SampleDataProvider.generateSampleInventoryData();
        applyFilters();
    }
}

