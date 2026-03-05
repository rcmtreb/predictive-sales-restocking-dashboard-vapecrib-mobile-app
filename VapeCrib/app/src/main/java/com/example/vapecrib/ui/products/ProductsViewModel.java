package com.example.vapecrib.ui.products;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.model.InventoryItem;
import com.example.vapecrib.network.PagedProductsResponse;
import com.example.vapecrib.network.ProductApiRecord;
import com.example.vapecrib.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ProductsViewModel extends AndroidViewModel {

    private final MutableLiveData<List<InventoryItem>> filteredInventory = new MutableLiveData<>();
    private final MutableLiveData<String>              itemCountText     = new MutableLiveData<>("Loading...");
    private final MutableLiveData<String>              apiError          = new MutableLiveData<>();

    /** Full unfiltered list from server; local filtering applied on top. */
    private List<InventoryItem> allInventory = new ArrayList<>();
    private String currentSearchQuery  = "";
    private String currentStockFilter  = "All";
    private boolean loaded = false;

    private final ExecutorService bgExecutor = Executors.newFixedThreadPool(2);

    public static final String[] STOCK_FILTER_OPTIONS =
            {"All", "Critical", "High", "Medium", "Low"};

    public ProductsViewModel(Application application) {
        super(application);
        bgExecutor.execute(this::loadFromApi);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<List<InventoryItem>> getFilteredInventory() { return filteredInventory; }
    public LiveData<String>              getItemCountText()      { return itemCountText;      }
    public LiveData<String>              getApiError()           { return apiError;           }

    public void setSearchQuery(String query) {
        currentSearchQuery = (query == null) ? "" : query.trim();
        applyFilters();
    }

    public void setStockLevelFilter(String level) {
        currentStockFilter = (level == null) ? "All" : level;
        applyFilters();
    }

    /** Pull-to-refresh: re-fetch all pages from the server. */
    public void refreshData() {
        itemCountText.setValue("Loading...");
        bgExecutor.execute(this::loadFromApi);
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadFromApi() {
        try {
            List<InventoryItem> items = new ArrayList<>();
            int page = 1;
            while (true) {
                Response<PagedProductsResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getProducts(page, 100, null, "name")
                        .execute();
                if (!resp.isSuccessful()) break;
                PagedProductsResponse body = resp.body();
                if (body == null || body.getData() == null) break;
                for (ProductApiRecord p : body.getData()) {
                    items.add(toInventoryItem(p));
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }
            allInventory = items;
            loaded = true;
            applyFiltersOnBackground();
        } catch (Exception e) {
            apiError.postValue("Could not load products: " + e.getMessage());
            itemCountText.postValue("0 items");
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void applyFilters() {
        if (!loaded) return;
        List<InventoryItem> result = buildFilteredList();
        filteredInventory.setValue(result);
        int count = result.size();
        itemCountText.setValue(count + " item" + (count != 1 ? "s" : ""));
    }

    /** Thread-safe version used after background loading finishes. */
    private void applyFiltersOnBackground() {
        List<InventoryItem> result = buildFilteredList();
        filteredInventory.postValue(result);
        int count = result.size();
        itemCountText.postValue(count + " item" + (count != 1 ? "s" : ""));
    }

    private List<InventoryItem> buildFilteredList() {
        List<InventoryItem> result = new ArrayList<>();
        String lowerQuery = currentSearchQuery.toLowerCase();

        for (InventoryItem item : allInventory) {
            // Stock level filter
            if (!currentStockFilter.equals("All")) {
                if (!item.getStockLevel().getDisplayName()
                        .equalsIgnoreCase(currentStockFilter)) continue;
            }
            // Name / category search
            if (!lowerQuery.isEmpty()) {
                boolean nameMatch     = item.getProductName().toLowerCase().contains(lowerQuery);
                boolean categoryMatch = item.getCategory() != null
                        && item.getCategory().toLowerCase().contains(lowerQuery);
                if (!nameMatch && !categoryMatch) continue;
            }
            result.add(item);
        }
        return result;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Maps a server ProductApiRecord to the InventoryItem model used by the adapter.
     *
     * Stock level thresholds match the web-app convention:
     *   CRITICAL : stock == 0
     *   HIGH     : stock 1-5
     *   MEDIUM   : stock 6-10   (web dashboard "low_stock" threshold is ≤ 10)
     *   LOW      : stock > 10   (adequate stock)
     */
    private static InventoryItem toInventoryItem(ProductApiRecord p) {
        int stock = p.currentStock;
        InventoryItem.StockLevel level;
        if (stock == 0)       level = InventoryItem.StockLevel.CRITICAL;
        else if (stock <= 5)  level = InventoryItem.StockLevel.HIGH;
        else if (stock <= 10) level = InventoryItem.StockLevel.MEDIUM;
        else                  level = InventoryItem.StockLevel.LOW;

        return new InventoryItem(
                p.name,
                stock,
                10,   // minStockLevel — matches web "low_stock" threshold
                100,  // maxStockLevel — display only
                p.category != null ? p.category : "",
                level,
                false // isExpiringSoon — not available from the products endpoint
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bgExecutor.shutdownNow();
    }
}


