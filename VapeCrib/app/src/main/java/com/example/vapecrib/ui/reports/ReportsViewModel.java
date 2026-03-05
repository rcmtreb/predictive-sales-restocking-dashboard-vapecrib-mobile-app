package com.example.vapecrib.ui.reports;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.model.SalesData;
import com.example.vapecrib.network.ForecastApiRecord;
import com.example.vapecrib.network.PagedForecastResponse;
import com.example.vapecrib.network.PagedProductsResponse;
import com.example.vapecrib.network.PagedSalesResponse;
import com.example.vapecrib.network.ProductApiRecord;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.SaleApiRecord;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ReportsViewModel extends AndroidViewModel {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private final MutableLiveData<String> criticalCount  = new MutableLiveData<>();
    private final MutableLiveData<String> highCount      = new MutableLiveData<>();
    private final MutableLiveData<String> mediumCount    = new MutableLiveData<>();
    private final MutableLiveData<String> lowCount       = new MutableLiveData<>();
    private final MutableLiveData<String> expiringItems  = new MutableLiveData<>();
    private final MutableLiveData<String> totalRevenue   = new MutableLiveData<>();
    private final MutableLiveData<String> totalProducts  = new MutableLiveData<>();
    private final MutableLiveData<String> totalOrders    = new MutableLiveData<>();
    /** Non-null when the API call fails — observed by ReportsFragment to show a Toast. */
    private final MutableLiveData<String> apiError       = new MutableLiveData<>();

    private List<SalesData> allSalesData;
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;

    public ReportsViewModel(Application application) {
        super(application);
        allSalesData     = new ArrayList<>();
        currentStartDate = LocalDate.of(2022, 1, 1);
        currentEndDate   = LocalDate.now();
        // Show loading placeholders while API fetches
        criticalCount .setValue("Critical: ...");
        highCount     .setValue("High: ...");
        mediumCount   .setValue("Medium: ...");
        lowCount      .setValue("Low: ...");
        expiringItems .setValue("Expiring Soon: ...");
        totalRevenue  .setValue("Loading...");
        totalProducts .setValue("...");
        totalOrders   .setValue("...");
        executor.execute(this::fetchInventoryCounts);
        executor.execute(() -> fetchFromApi(currentStartDate, currentEndDate));
    }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        executor.execute(() -> fetchFromApi(start, end));
    }

    // ── Inventory counts from products API ─────────────────────────────────────────

    /** Fetches products and counts by stock level. Must be called on a background thread. */
    private void fetchInventoryCounts() {
        try {
            int critical = 0, high = 0, medium = 0, low = 0;
            int page = 1;
            while (true) {
                retrofit2.Response<PagedProductsResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getProducts(page, 100, null, "name")
                        .execute();
                if (!resp.isSuccessful()) break;
                PagedProductsResponse body = resp.body();
                if (body == null || body.getData() == null) break;
                for (ProductApiRecord p : body.getData()) {
                    int stock = p.currentStock;
                    if (stock == 0)       critical++;
                    else if (stock <= 5)  high++;
                    else if (stock <= 10) medium++;
                    else                  low++;
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }
            criticalCount .postValue("Critical: " + critical);
            highCount     .postValue("High: "     + high);
            mediumCount   .postValue("Medium: "   + medium);
            lowCount      .postValue("Low: "      + low);
            expiringItems .postValue("Expiring Soon: 0"); // not available from current API
        } catch (Exception e) {
            criticalCount.postValue("Critical: N/A");
        }
    }

    private void computeRevenueTotals() {
        float rev = 0;
        int   prod = 0;
        int   orders = 0;
        for (SalesData d : allSalesData) {
            if (!d.getDate().isBefore(currentStartDate) && !d.getDate().isAfter(currentEndDate)) {
                rev   += d.getRevenue();
                prod  += d.getProductsCount();
                orders += d.getProductsCount(); // total transaction count, matches web's total_sales
            }
        }
        totalRevenue.postValue(String.format("P%,.0f", rev));
        totalProducts.postValue(String.valueOf(prod));
        totalOrders.postValue(String.valueOf(orders));
    }

    // ── Flask API fetch ───────────────────────────────────────────────────────

    /**
     * Must be called from a background thread.
     * Fetches all pages of sales data for the given range and updates LiveData.
     */
    private void fetchFromApi(LocalDate start, LocalDate end) {
        try {
            String fromDate = start.toString();
            String toDate   = end.toString();

            List<SaleApiRecord> records = new ArrayList<>();
            int page = 1;

            while (true) {
                Response<PagedSalesResponse> response = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getSales(fromDate, toDate, null, page, 100)
                        .execute();

                if (!response.isSuccessful()) break;
                PagedSalesResponse body = response.body();
                if (body == null || body.getData() == null) break;

                records.addAll(body.getData());
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }

            if (!records.isEmpty()) {
                // Group transactions by date → one SalesData per day
                Map<LocalDate, float[]> grouped = new LinkedHashMap<>();
                for (SaleApiRecord r : records) {
                    if (r.date == null) continue;
                    try {
                        // Flask returns full ISO datetime e.g. "2025-03-01T14:30:00"
                        // LocalDate.parse only handles "2025-03-01" — strip the time part
                        LocalDate d = LocalDate.parse(r.date.substring(0, 10));
                        float[] agg = grouped.computeIfAbsent(d, k -> new float[3]);
                        agg[0] += r.actualSales;   // quantity
                        agg[1] += r.revenue;       // total
                        agg[2] += 1;               // transaction count
                    } catch (Exception ignored) {}
                }

                // Fetch forecast data for the same range
                Map<LocalDate, Float> forecastByDate = fetchForecastMap(fromDate, toDate);

                List<SalesData> fresh = new ArrayList<>();
                for (Map.Entry<LocalDate, float[]> e : grouped.entrySet()) {
                    float[] agg = e.getValue();
                    float forecast = forecastByDate.getOrDefault(e.getKey(), 0f);
                    fresh.add(new SalesData(e.getKey(),
                            agg[0], forecast, agg[1], (int) agg[2]));
                }
                allSalesData = fresh;
                computeRevenueTotals();
            }

        } catch (IOException e) {
            apiError.postValue("Could not reach server. Showing cached data.");
        } catch (Exception e) {
            apiError.postValue("Data sync error: " + e.getMessage());
        }
    }
    /**
     * Fetches all Forecast rows for the date range and returns date → predicted_quantity.
     * Must be called on a background thread. Returns empty map on failure.
     */
    private Map<LocalDate, Float> fetchForecastMap(String fromDate, String toDate) {
        Map<LocalDate, Float> result = new LinkedHashMap<>();
        try {
            int page = 1;
            while (true) {
                Response<PagedForecastResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getForecast(fromDate, toDate, null, page, 100)
                        .execute();
                if (!resp.isSuccessful()) break;
                PagedForecastResponse body = resp.body();
                if (body == null || body.getData() == null) break;
                for (ForecastApiRecord f : body.getData()) {
                    String dateStr = (f.periodKey != null) ? f.periodKey
                            : (f.forecastDate != null ? f.forecastDate.substring(0, 10) : null);
                    if (dateStr == null) continue;
                    LocalDate d = LocalDate.parse(dateStr);
                    result.merge(d, f.predictedQuantity, Float::sum);
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }
        } catch (Exception ignored) {}
        return result;
    }
    // ── Getters ───────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }

    public LiveData<String> getCriticalCount()  { return criticalCount;  }
    public LiveData<String> getHighCount()      { return highCount;      }
    public LiveData<String> getMediumCount()    { return mediumCount;    }
    public LiveData<String> getLowCount()       { return lowCount;       }
    public LiveData<String> getExpiringItems()  { return expiringItems;  }
    public LiveData<String> getTotalRevenue()   { return totalRevenue;   }
    public LiveData<String> getTotalProducts()  { return totalProducts;  }
    public LiveData<String> getTotalOrders()    { return totalOrders;    }
    public LiveData<String> getApiError()       { return apiError;       }
}

