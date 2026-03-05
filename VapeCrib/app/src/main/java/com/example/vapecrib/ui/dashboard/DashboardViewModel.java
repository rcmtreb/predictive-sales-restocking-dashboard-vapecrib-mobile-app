package com.example.vapecrib.ui.dashboard;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.data.db.AppDatabase;
import com.example.vapecrib.data.db.SalesDataEntity;
import com.example.vapecrib.model.SalesData;
import com.example.vapecrib.network.ForecastApiRecord;
import com.example.vapecrib.network.PagedForecastResponse;
import com.example.vapecrib.network.PagedSalesResponse;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.SaleApiRecord;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

public class DashboardViewModel extends AndroidViewModel {

    private final MutableLiveData<String> totalSalesProducts  = new MutableLiveData<>("Loading...");
    private final MutableLiveData<String> totalSalesRevenue   = new MutableLiveData<>("Loading...");
    private final MutableLiveData<String> totalInventoryValue = new MutableLiveData<>("Loading...");
    private final MutableLiveData<String> forecastAccuracy    = new MutableLiveData<>("Loading...");
    private final MutableLiveData<String> activeAlerts        = new MutableLiveData<>("-");
    private final MutableLiveData<String> expiringStocks      = new MutableLiveData<>("-");
    private final MutableLiveData<String> criticalCount       = new MutableLiveData<>("Critical: -");
    private final MutableLiveData<String> highCount           = new MutableLiveData<>("High: -");
    private final MutableLiveData<String> mediumCount         = new MutableLiveData<>("Medium: -");
    private final MutableLiveData<LineData> salesTrendData         = new MutableLiveData<>();
    private final MutableLiveData<BarData>  monthlyPerformanceData = new MutableLiveData<>();
    /** Non-null means an API error occurred; the Fragment shows it as a Toast. */
    private final MutableLiveData<String>   apiError               = new MutableLiveData<>();
    public  LiveData<String>   getApiError() { return apiError; }

    private final AppDatabase db;
    private List<SalesData> allSalesData = new ArrayList<>();
    private int cachedCritical, cachedHigh, cachedMedium, cachedExpiring;
    /** -1f means no API accuracy available yet — fall back to local MAPE calculation. */
    private float cachedForecastAccuracy = -1f;
    private boolean dataLoaded = false;
    // Default "all time" range based on CSV/sample coverage
    private LocalDate currentStartDate = LocalDate.of(2022, 1, 1);
    private LocalDate currentEndDate   = LocalDate.of(2026, 3, 31);

    public DashboardViewModel(Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        AppDatabase.dbExecutor.execute(() -> {
            // ── Sales records ────────────────────────────────────────────
            List<SalesDataEntity> entities = db.salesDataDao().getAll();
            List<SalesData> data = new ArrayList<>();
            for (SalesDataEntity e : entities) {
                data.add(new SalesData(
                        LocalDate.parse(e.date),
                        e.actualSales, e.forecastedSales,
                        e.revenue, e.productsCount));
            }
            allSalesData = data;

            // ── Inventory counts from DB ─────────────────────────────────
            cachedCritical = db.inventoryItemDao().countByLevel("CRITICAL");
            cachedHigh     = db.inventoryItemDao().countByLevel("HIGH");
            cachedMedium   = db.inventoryItemDao().countByLevel("MEDIUM");
            cachedExpiring = db.inventoryItemDao().countExpiring();
            dataLoaded = true;

            computeAndPost(); // Show local data immediately

            // Then try to refresh from the Flask API
            fetchFromApi(currentStartDate, currentEndDate);
        });
    }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        if (dataLoaded) {
            // Recompute from local cache immediately, then refresh from API
            AppDatabase.dbExecutor.execute(() -> {
                computeAndPost();
                fetchFromApi(start, end);
            });
        }
    }

    // ── Flask API fetch ───────────────────────────────────────────────────────

    /**
     * Fetches all pages of sales data for the given date range from the Flask API.
     * Must be called from a background thread (uses Retrofit execute()).
     * On success, replaces allSalesData and recomputes KPIs + charts.
     * On failure, keeps existing local/DB data and posts an error message.
     */
    private void fetchFromApi(LocalDate start, LocalDate end) {
        try {
            String fromDate = start.toString(); // "YYYY-MM-DD"
            String toDate   = end.toString();

            List<SaleApiRecord> records = new ArrayList<>();
            int page = 1;

            // Paginated fetch — loop until all pages are collected
            while (true) {
                Response<PagedSalesResponse> response = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getSales(fromDate, toDate, page, 100)
                        .execute();

                if (!response.isSuccessful()) break; // e.g. 401 after exhausting retries
                PagedSalesResponse body = response.body();
                if (body == null || body.getData() == null) break;

                records.addAll(body.getData());
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }

            if (!records.isEmpty()) {
                // Group transactions by date → one SalesData per day
                // Flask returns one row per transaction; the chart needs daily totals.
                Map<LocalDate, float[]> grouped = new LinkedHashMap<>();
                // float[] layout: [0]=totalQuantity, [1]=totalRevenue, [2]=productCount
                for (SaleApiRecord r : records) {
                    if (r.date == null) continue;
                    try {
                        LocalDate d = LocalDate.parse(r.date);
                        float[] agg = grouped.computeIfAbsent(d, k -> new float[3]);
                        agg[0] += r.actualSales;   // quantity
                        agg[1] += r.revenue;       // total
                        agg[2] += 1;               // transaction count as product count proxy
                    } catch (Exception ignored) {}
                }

                // Fetch forecast data for the same range and group by date
                Map<LocalDate, Float> forecastByDate = fetchForecastMap(fromDate, toDate);

                List<SalesData> fresh = new ArrayList<>();
                for (Map.Entry<LocalDate, float[]> e : grouped.entrySet()) {
                    float[] agg = e.getValue();
                    float forecast = forecastByDate.getOrDefault(e.getKey(), 0f);
                    fresh.add(new SalesData(e.getKey(),
                            agg[0],    // actualSales = total units sold that day
                            forecast,  // forecastedSales from Forecast table
                            agg[1],    // revenue = sum of totals that day
                            (int) agg[2]));
                }
                allSalesData = fresh;
                computeAndPost();
            }
        } catch (IOException e) {
            // Network error — keep existing data, inform UI
            apiError.postValue("Could not reach server. Showing cached data.");
        } catch (Exception e) {
            apiError.postValue("Data sync error: " + e.getMessage());
        }
    }

    /**
     * Fetches all Forecast rows for the date range and returns a map of date → total
     * predicted_quantity. Must be called on a background thread.
     * Returns an empty map on failure (so sales data still renders).
     */
    private Map<LocalDate, Float> fetchForecastMap(String fromDate, String toDate) {
        Map<LocalDate, Float> result = new LinkedHashMap<>();
        // accSum[0]=sum of accuracy values, accSum[1]=count
        float[] accSum = {0f, 0f};
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
                    // period_key is already "YYYY-MM-DD"; forecast_date may have time component
                    String dateStr = (f.periodKey != null) ? f.periodKey
                            : (f.forecastDate != null ? f.forecastDate.substring(0, 10) : null);
                    if (dateStr == null) continue;
                    LocalDate d = LocalDate.parse(dateStr);
                    result.merge(d, f.predictedQuantity, Float::sum);
                    // Accumulate stored accuracy for averaging
                    if (f.accuracy > 0) {
                        accSum[0] += f.accuracy;
                        accSum[1]++;
                    }
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }
        } catch (Exception ignored) { /* silent: forecast missing → 0f fallback */ }
        // Store average accuracy from Flask — used instead of recalculating in computeAndPost()
        if (accSum[1] > 0) cachedForecastAccuracy = accSum[0] / accSum[1];
        return result;
    }

    private void computeAndPost() {
        List<SalesData> data = getFilteredData();
        if (data.isEmpty()) { resetValues(); return; }

        int   totalProducts = 0;
        float totalRevenue  = 0;
        float totalForecast = 0;
        float totalActual   = 0;

        for (SalesData d : data) {
            totalProducts += d.getProductsCount();
            totalRevenue  += d.getRevenue();
            totalForecast += d.getForecastedSales();
            totalActual   += d.getActualSales();
        }

        // Use Flask-stored accuracy if available; otherwise compute from local data
        float accuracy;
        if (cachedForecastAccuracy >= 0) {
            accuracy = cachedForecastAccuracy;  // matches web app exactly
        } else {
            accuracy = totalActual > 0
                    ? Math.max(0, 100f - (Math.abs(totalActual - totalForecast) / totalActual) * 100f)
                    : 87f;
        }

        totalSalesProducts.postValue(String.valueOf(totalProducts));
        totalSalesRevenue.postValue(String.format("P%,.0f", totalRevenue));
        totalInventoryValue.postValue(String.format("P%,.0f", totalRevenue * 0.4f));
        forecastAccuracy.postValue(String.format("%.1f%%", accuracy));
        activeAlerts.postValue(String.valueOf(cachedCritical > 0 ? cachedCritical : 0));
        expiringStocks.postValue(String.valueOf(cachedExpiring));
        criticalCount.postValue("Critical: " + cachedCritical);
        highCount.postValue("High: "     + cachedHigh);
        mediumCount.postValue("Medium: " + cachedMedium);

        buildSalesTrendChart(data);
        buildMonthlyPerformanceChart(data);
    }

    private void buildSalesTrendChart(List<SalesData> data) {
        List<Entry> actualEntries   = new ArrayList<>();
        List<Entry> forecastEntries = new ArrayList<>();
        int step = Math.max(1, data.size() / 60);
        int idx  = 0;
        for (int i = 0; i < data.size(); i += step) {
            SalesData d = data.get(i);
            actualEntries.add(new Entry(idx, d.getActualSales()));
            forecastEntries.add(new Entry(idx, d.getForecastedSales()));
            idx++;
        }

        LineDataSet actualSet = new LineDataSet(actualEntries, "Actual Sales");
        actualSet.setColor(android.graphics.Color.parseColor("#FF9800"));
        actualSet.setLineWidth(2f);
        actualSet.setDrawValues(false);
        actualSet.setDrawCircles(false);

        LineDataSet forecastSet = new LineDataSet(forecastEntries, "Forecast");
        forecastSet.setColor(android.graphics.Color.parseColor("#2196F3"));
        forecastSet.setLineWidth(2f);
        forecastSet.setDrawValues(false);
        forecastSet.setDrawCircles(false);

        LineData lineData = new LineData(actualSet, forecastSet);
        lineData.setValueTextColor(android.graphics.Color.WHITE);
        salesTrendData.postValue(lineData);
    }

    private void buildMonthlyPerformanceChart(List<SalesData> data) {
        Map<YearMonth, Float> monthly = new LinkedHashMap<>();
        for (SalesData d : data) {
            YearMonth ym = YearMonth.from(d.getDate());
            monthly.merge(ym, d.getRevenue(), Float::sum);
        }
        List<BarEntry> entries = new ArrayList<>();
        int i = 0;
        for (Float rev : monthly.values()) entries.add(new BarEntry(i++, rev / 1000f));

        BarDataSet barSet = new BarDataSet(entries, "Monthly Revenue (P1k)");
        barSet.setColor(android.graphics.Color.parseColor("#4CAF50"));
        barSet.setDrawValues(false);
        BarData barData = new BarData(barSet);
        barData.setBarWidth(0.7f);
        monthlyPerformanceData.postValue(barData);
    }

    private List<SalesData> getFilteredData() {
        List<SalesData> out = new ArrayList<>();
        for (SalesData d : allSalesData) {
            if (!d.getDate().isBefore(currentStartDate) && !d.getDate().isAfter(currentEndDate))
                out.add(d);
        }
        return out;
    }

    private void resetValues() {
        totalSalesProducts.postValue("0");
        totalSalesRevenue.postValue("P0.00");
        totalInventoryValue.postValue("P0.00");
        forecastAccuracy.postValue("0%");
        activeAlerts.postValue("0");
        expiringStocks.postValue("0");
        criticalCount.postValue("Critical: 0");
        highCount.postValue("High: 0");
        mediumCount.postValue("Medium: 0");
        salesTrendData.postValue(null);
        monthlyPerformanceData.postValue(null);
    }

    public LiveData<String>   getTotalSalesProducts()      { return totalSalesProducts;      }
    public LiveData<String>   getTotalSalesRevenue()       { return totalSalesRevenue;       }
    public LiveData<String>   getTotalInventoryValue()     { return totalInventoryValue;     }
    public LiveData<String>   getForecastAccuracy()        { return forecastAccuracy;        }
    public LiveData<String>   getActiveAlerts()            { return activeAlerts;            }
    public LiveData<String>   getExpiringStocks()          { return expiringStocks;          }
    public LiveData<String>   getCriticalCount()           { return criticalCount;           }
    public LiveData<String>   getHighCount()               { return highCount;               }
    public LiveData<String>   getMediumCount()             { return mediumCount;             }
    public LiveData<LineData> getSalesTrendData()          { return salesTrendData;          }
    public LiveData<BarData>  getMonthlyPerformanceData()  { return monthlyPerformanceData;  }
}
