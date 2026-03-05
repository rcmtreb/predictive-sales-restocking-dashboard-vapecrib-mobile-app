package com.example.vapecrib.ui.dashboard;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.model.SalesData;
import com.example.vapecrib.network.DashboardResponse;
import com.example.vapecrib.network.ForecastAccuracyResponse;
import com.example.vapecrib.network.ForecastApiRecord;
import com.example.vapecrib.network.PagedForecastResponse;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.SalesChartResponse;
import com.example.vapecrib.network.TopProduct;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final MutableLiveData<List<TopProduct>> topProducts = new MutableLiveData<>();
    /** Non-null means an API error occurred; the Fragment shows it as a Toast. */
    private final MutableLiveData<String>   apiError               = new MutableLiveData<>();
    public  LiveData<String>   getApiError() { return apiError; }

    private final ExecutorService bgExecutor = Executors.newFixedThreadPool(2);
    private List<SalesData> allSalesData = new ArrayList<>();
    private int cachedCritical, cachedHigh, cachedMedium, cachedExpiring;
    /** -1f means no API value available yet — fall back to local estimate. */
    private float cachedForecastAccuracy = -1f;
    private float cachedInventoryValue   = -1f;
    private boolean dataLoaded = false;
    // Default range: Last 7 Days — shows the most recent activity on first open.
    private LocalDate currentStartDate = LocalDate.now().minusDays(6);
    private LocalDate currentEndDate   = LocalDate.now();

    public DashboardViewModel(Application application) {
        super(application);
        // Do NOT open the Room DB — all data comes from the Flask API.
        // Calling AppDatabase.getInstance() would trigger CSV seeding (stale sample data).
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        bgExecutor.execute(() -> {
            dataLoaded = true;
            // Skip showing local Room DB / CSV sample data.
            // Go straight to the server so only real data is ever displayed.
            fetchFromApi(currentStartDate, currentEndDate);
            fetchDashboard();
        });
    }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        if (dataLoaded) {
            bgExecutor.execute(() -> {
                computeAndPost();
                fetchFromApi(start, end);
                fetchDashboard();
            });
        }
    }

    // ── Flask API fetch ───────────────────────────────────────────────────────

    /**
     * Fetches pre-aggregated daily + monthly totals from /sales/chart in ONE request.
     * Must be called from a background thread.
     * On success, replaces allSalesData and recomputes KPIs + charts.
     */
    private void fetchFromApi(LocalDate start, LocalDate end) {
        try {
            String fromDate = start.toString();
            String toDate   = end.toString();

            Response<SalesChartResponse> response = RetrofitClient
                    .getInstance(getApplication())
                    .getApi()
                    .getSalesChart(fromDate, toDate, null)
                    .execute();

            if (!response.isSuccessful() || response.body() == null
                    || response.body().data == null) {
                int code = response.code();
                apiError.postValue("Server error " + code + ". Pull down to retry.");
                return;
            }

            SalesChartResponse.Data chart = response.body().data;

            if (chart.daily == null || chart.daily.isEmpty()) {
                apiError.postValue("No sales data for this period.");
                allSalesData = new ArrayList<>();
                computeAndPost();
                return;
            }

            // Fetch forecast data for the same range (still needed for the trend chart)
            Map<LocalDate, Float> forecastByDate = fetchForecastMap(fromDate, toDate);

            List<SalesData> fresh = new ArrayList<>();
            for (SalesChartResponse.DailyPoint pt : chart.daily) {
                if (pt.date == null) continue;
                try {
                    LocalDate d        = LocalDate.parse(pt.date); // always "YYYY-MM-DD"
                    float     forecast = forecastByDate.getOrDefault(d, 0f);
                    fresh.add(new SalesData(d,
                            pt.quantity,   // actualSales = units sold that day
                            forecast,
                            pt.revenue,    // revenue = daily total
                            pt.quantity)); // productsCount = units sold (meaningful KPI)
                } catch (Exception ignored) {}
            }

            allSalesData = fresh;
            computeAndPost();

        } catch (IOException e) {
            apiError.postValue("Could not reach server. Pull down to retry.");
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
                    // period_key is already "YYYY-MM-DD"; forecast_date may have time component
                    String dateStr = (f.periodKey != null) ? f.periodKey
                            : (f.forecastDate != null ? f.forecastDate.substring(0, 10) : null);
                    if (dateStr == null) continue;
                    LocalDate d = LocalDate.parse(dateStr);
                    result.merge(d, f.predictedQuantity, Float::sum);
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }
        } catch (Exception ignored) { /* silent: forecast missing → 0f fallback */ }
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
                    : 0f;
        }

        totalSalesProducts.postValue(String.valueOf(totalProducts));
        totalSalesRevenue.postValue(String.format("P%,.0f", totalRevenue));
        String invVal = cachedInventoryValue >= 0
                ? String.format("P%,.0f", cachedInventoryValue)
                : "Loading...";
        totalInventoryValue.postValue(invVal);
        forecastAccuracy.postValue(String.format("%.1f%%", accuracy));
        activeAlerts.postValue(String.valueOf(cachedCritical > 0 ? cachedCritical : 0));
        expiringStocks.postValue(String.valueOf(cachedExpiring));
        criticalCount.postValue("Critical: " + cachedCritical);
        highCount.postValue("Warning: "  + cachedHigh);
        mediumCount.postValue("Info: "    + cachedMedium);

        buildSalesTrendChart(data);
        buildMonthlyPerformanceChart(data);
    }

    /**
     * Fetches the /dashboard summary once to update inventory value, alert counts,
     * and expiring-batch count from real server data.
     * Must be called from a background thread.
     */
    private void fetchDashboard() {
        try {
            Response<DashboardResponse> resp = RetrofitClient
                    .getInstance(getApplication())
                    .getApi()
                    .getDashboard()
                    .execute();
            if (!resp.isSuccessful() || resp.body() == null) return;
            DashboardResponse.Data d = resp.body().data;
            if (d == null) return;

            cachedInventoryValue = d.inventoryValue;
            cachedExpiring       = d.expiringSoon;

            if (d.alertsBySeverity != null) {
                cachedCritical = d.alertsBySeverity.critical;
                cachedHigh     = d.alertsBySeverity.warning;
                cachedMedium   = d.alertsBySeverity.info;
            }

            // Re-post updated alert / inventory values (keep chart data as-is)
            activeAlerts.postValue(String.valueOf(d.activeAlerts));
            expiringStocks.postValue(String.valueOf(cachedExpiring));
            criticalCount.postValue("Critical: " + cachedCritical);
            highCount.postValue("Warning: "  + cachedHigh);
            mediumCount.postValue("Info: "    + cachedMedium);
            totalInventoryValue.postValue(String.format("P%,.0f", cachedInventoryValue));
            if (d.topProducts != null) topProducts.postValue(d.topProducts);
        } catch (Exception ignored) { /* network error — keep local values */ }

        // Fetch MAPE-based forecast accuracy using the same method as the web dashboard.
        // days_back is computed from the currently-selected date range so the accuracy
        // value changes when the user picks a different period (7d / 30d / 3m etc.).
        try {
            int daysBack = (int) Math.max(1, ChronoUnit.DAYS.between(currentStartDate, currentEndDate));
            Response<ForecastAccuracyResponse> accResp = RetrofitClient
                    .getInstance(getApplication())
                    .getApi()
                    .getForecastAccuracy(daysBack)
                    .execute();
            if (accResp.isSuccessful() && accResp.body() != null
                    && accResp.body().data != null) {
                cachedForecastAccuracy = accResp.body().data.accuracy;
                computeAndPost(); // refresh the displayed accuracy card
            }
        } catch (Exception ignored) { /* keep local estimate if server unreachable */ }
    }

    /** Clear the API error after it has been consumed by the UI to prevent re-showing
     *  on screen rotation or fragment back-stack restoration. */
    public void clearApiError() {
        apiError.postValue(null);
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
        // Inventory value is the CURRENT stock value — not period-dependent.
        // Preserve the cached value so switching to a period with no sales doesn't wipe it.
        totalInventoryValue.postValue(
            cachedInventoryValue >= 0 ? String.format("P%,.0f", cachedInventoryValue) : "Loading...");
        forecastAccuracy.postValue("0%");
        // Also preserve alert counts if cached
        activeAlerts.postValue(String.valueOf(cachedCritical > 0 ? cachedCritical : 0));
        expiringStocks.postValue(String.valueOf(cachedExpiring));
        criticalCount.postValue("Critical: " + cachedCritical);
        highCount.postValue("Warning: " + cachedHigh);
        mediumCount.postValue("Info: " + cachedMedium);
        salesTrendData.postValue(null);
        monthlyPerformanceData.postValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bgExecutor.shutdownNow();
    }

    /**
     * Re-fetches data for the currently-selected date range.
     * Called from DashboardFragment.onResume() so the dashboard always shows
     * fresh data when the user navigates back to it without a full reload.
     */
    public void refresh() {
        bgExecutor.execute(() -> {
            fetchFromApi(currentStartDate, currentEndDate);
            fetchDashboard();
        });
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
    public LiveData<List<TopProduct>> getTopProducts() { return topProducts; }
}
