package com.example.vapecrib.ui.reports;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.data.SampleDataProvider;
import com.example.vapecrib.model.SalesData;
import com.example.vapecrib.network.ForecastApiRecord;
import com.example.vapecrib.network.PagedForecastResponse;
import com.example.vapecrib.network.PagedSalesResponse;
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

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        allSalesData = SampleDataProvider.generateSampleSalesData();
        currentStartDate = LocalDate.of(2022, 1, 1);
        currentEndDate   = LocalDate.of(2026, 3, 31);
        // Compute from local data immediately, then refresh from API
        loadData();
        executor.execute(() -> fetchFromApi(currentStartDate, currentEndDate));
    }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        loadData(); // fast local pass first
        executor.execute(() -> fetchFromApi(start, end));
    }

    // ── Local / sample data ───────────────────────────────────────────────────

    private void loadData() {
        // Inventory counts are time-independent (current stock snapshot)
        criticalCount.postValue("Critical: "      + SampleDataProvider.getCriticalCount());
        highCount.postValue("High: "              + SampleDataProvider.getHighCount());
        mediumCount.postValue("Medium: "          + SampleDataProvider.getMediumCount());
        lowCount.postValue("Low: "                + SampleDataProvider.getLowCount());
        expiringItems.postValue("Expiring Soon: " + SampleDataProvider.getExpiringCount());

        computeRevenueTotals();
    }

    private void computeRevenueTotals() {
        float rev = 0;
        int   prod = 0;
        int   orders = 0;
        for (SalesData d : allSalesData) {
            if (!d.getDate().isBefore(currentStartDate) && !d.getDate().isAfter(currentEndDate)) {
                rev   += d.getRevenue();
                prod  += d.getProductsCount();
                orders++;
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
                        .getSales(fromDate, toDate, page, 100)
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
                        LocalDate d = LocalDate.parse(r.date);
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
                        .getForecast(fromDate, toDate, page, 100)
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

