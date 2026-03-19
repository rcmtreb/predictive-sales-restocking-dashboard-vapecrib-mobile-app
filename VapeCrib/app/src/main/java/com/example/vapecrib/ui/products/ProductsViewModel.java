package com.example.vapecrib.ui.products;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.model.InventoryItem;
import com.example.vapecrib.network.AddProductRequest;
import com.example.vapecrib.network.AddProductResponse;
import com.example.vapecrib.network.AlertsResponse;
import com.example.vapecrib.network.PagedProductsResponse;
import com.example.vapecrib.network.ProductApiRecord;
import com.example.vapecrib.network.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
            // ── Fetch active alerts first to build productId → severity map ──────
            // Uses the existing /alerts endpoint (no server change needed).
            // Severity priority: CRITICAL > HIGH > MEDIUM — keep the worst level
            // if a product somehow has more than one active alert.
            Map<Integer, String> alertMap = new HashMap<>();
            try {
                Response<AlertsResponse> alertResp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getAlerts(true, 200)
                        .execute();
                if (alertResp.isSuccessful() && alertResp.body() != null
                        && alertResp.body().data != null) {
                    Map<String, Integer> rank = new HashMap<>();
                    rank.put("CRITICAL", 3); rank.put("HIGH", 2); rank.put("MEDIUM", 1);
                    for (AlertsResponse.AlertItem a : alertResp.body().data) {
                        String existing = alertMap.get(a.productId);
                        int newRank = rank.getOrDefault(a.severity != null
                                ? a.severity.toUpperCase() : "", 0);
                        int curRank = rank.getOrDefault(existing != null
                                ? existing : "", 0);
                        if (newRank > curRank) {
                            alertMap.put(a.productId, a.severity.toUpperCase());
                        }
                    }
                }
            } catch (Exception ignored) { /* keep empty map — fall back to adequate */ }

            // ── Fetch paginated product catalogue ─────────────────────────────
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
                    items.add(toInventoryItem(p, alertMap));
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
     * Uses the alertMap built from GET /api/mobile/alerts (forecast-based severity,
     * identical to the web dashboard). Products with no active alert are "Low" (adequate).
     */
    private static InventoryItem toInventoryItem(ProductApiRecord p,
                                                  Map<Integer, String> alertMap) {
        int stock = p.currentStock;
        InventoryItem.StockLevel level;
        String severity = alertMap.get(p.id);
        if (severity != null) {
            switch (severity) {
                case "CRITICAL": level = InventoryItem.StockLevel.CRITICAL; break;
                case "HIGH":     level = InventoryItem.StockLevel.HIGH;     break;
                case "MEDIUM":   level = InventoryItem.StockLevel.MEDIUM;   break;
                default:         level = InventoryItem.StockLevel.LOW;      break;
            }
        } else {
            // No active alert → stock is sufficient per forecast model
            level = InventoryItem.StockLevel.LOW;
        }

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

    // ── Product creation ──────────────────────────────────────────────────────

    public interface CreateProductCallback {
        void onSuccess(String productName);
        void onError(String message);
    }

    public void createProduct(String name, String category, float unitCost, int currentStock,
                              CreateProductCallback callback) {
        AddProductRequest req = new AddProductRequest(name, category, unitCost, currentStock);
        RetrofitClient.getInstance(getApplication())
                .getApi()
                .createProduct(req)
                .enqueue(new Callback<AddProductResponse>() {
                    @Override
                    public void onResponse(Call<AddProductResponse> call,
                                           Response<AddProductResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().success) {
                            String created = response.body().data != null
                                    ? response.body().data.name : name;
                            callback.onSuccess(created);
                            // Refresh the list so the new product appears
                            bgExecutor.execute(ProductsViewModel.this::loadFromApi);
                        } else {
                            String err = "Failed to add product";
                            if (response.body() != null && response.body().error != null) {
                                err = response.body().error;
                            } else if (response.code() == 403) {
                                err = "You don't have permission to add products.";
                            } else if (response.code() == 409) {
                                err = "A product with that name already exists.";
                            }
                            callback.onError(err);
                        }
                    }

                    @Override
                    public void onFailure(Call<AddProductResponse> call, Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bgExecutor.shutdownNow();
    }
}


