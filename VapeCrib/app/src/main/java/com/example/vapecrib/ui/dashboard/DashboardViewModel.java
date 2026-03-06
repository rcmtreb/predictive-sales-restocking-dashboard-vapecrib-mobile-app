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
    /** UI label for the last-selected period, restored on navigation back. */
    private String currentPeriodLabel = "Last 7 Days";
    /** Server pre-aggregated monthly data; updated on every successful API fetch. */
    private List<SalesChartResponse.MonthlyPoint> latestMonthlyPoints = new ArrayList<>();

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

            // Discard this response if the user changed the period while the request was
            // in-flight — prevents a stale Last-7-Days fetch from overwriting the
            // Last-30-Days results that may have already been applied on the other thread.
            if (!start.equals(currentStartDate) || !end.equals(currentEndDate)) {
                return;
            }

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

            // Use server pre-aggregated monthly data directly for the bar chart
            latestMonthlyPoints = (chart.monthly != null) ? chart.monthly : new ArrayList<>();

            // Extend the forecast query window to include upcoming days so the trend
            // chart can show a forecast projection even when historical per-day records
            // are sparse. The extension matches the selected period length (capped at 30).
            long periodDays = Math.max(7, ChronoUnit.DAYS.between(start, end) + 1);
            LocalDate forecastQueryEnd = end.isBefore(LocalDate.now())
                    ? end
                    : LocalDate.now().plusDays(Math.min(periodDays, 30));
            Map<LocalDate, Float> forecastByDate = fetchForecastMap(fromDate, forecastQueryEnd.toString());

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

            // Append future forecast-only entries so the trend chart shows an upcoming
            // projection line beyond the last actual sale date — mirrors the web's
            // Daily Forecast chart which shows actual (past) + forecast (future).
            LocalDate today = LocalDate.now();
            if (!today.isAfter(forecastQueryEnd)) {
                for (LocalDate d = today.plusDays(1); !d.isAfter(forecastQueryEnd); d = d.plusDays(1)) {
                    float fc = forecastByDate.getOrDefault(d, 0f);
                    if (fc > 0f) {
                        fresh.add(new SalesData(d, 0, fc, 0f, 0));
                    }
                }
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

        buildSalesTrendChart(allSalesData);  // all data including future forecast entries
        buildMonthlyPerformanceChart(latestMonthlyPoints);
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
        // Compute the average unit price from historical days ONLY (revenue > 0),
        // so future-only forecast entries don't skew the divisor.
        float totalRev = 0f, totalQty = 0f;
        for (SalesData d : data) {
            if (d.getRevenue() > 0f) {
                totalRev += d.getRevenue();
                totalQty += d.getActualSales();
            }
        }
        float avgUnitPrice = totalQty > 0f ? totalRev / totalQty : 1f;

        List<Entry> actualEntries   = new ArrayList<>();
        List<Entry> forecastEntries = new ArrayList<>();
        int step = Math.max(1, data.size() / 60);
        int idx  = 0;
        for (int i = 0; i < data.size(); i += step) {
            SalesData d = data.get(i);
            // Only plot actual entry when there are real historical sales. Future-only
            // entries (revenue = 0, forecast > 0) are skipped for the actual line so
            // it stops naturally at the last real sale date — matches the web's null
            // handling which breaks the actual line at the end of historical data.
            if (d.getRevenue() > 0f) {
                actualEntries.add(new Entry(idx, d.getRevenue()));
            }
            // Forecasted Revenue = forecast units × avg price — mirrors web dataset[1]
            // "Forecasted Revenue". Also covers future projection entries.
            if (d.getForecastedSales() > 0f) {
                forecastEntries.add(new Entry(idx, d.getForecastedSales() * avgUnitPrice));
            }
            idx++;
        }

        // Colors and style match web's trendChart (Forecast Trend Analysis):
        //   Actual Revenue     → green  #34d399  (solid)
        //   Forecasted Revenue → purple #818cf8  (dashed, mirrors web borderDash [6,3])
        LineDataSet actualSet = new LineDataSet(actualEntries, "Actual Revenue");
        actualSet.setColor(android.graphics.Color.parseColor("#34d399"));
        actualSet.setLineWidth(2f);
        actualSet.setDrawValues(false);
        actualSet.setDrawCircles(false);

        LineDataSet forecastSet = new LineDataSet(forecastEntries, "Forecasted Revenue");
        forecastSet.setColor(android.graphics.Color.parseColor("#818cf8"));
        forecastSet.setLineWidth(2f);
        forecastSet.setDrawValues(false);
        forecastSet.setDrawCircles(false);
        forecastSet.enableDashedLine(12f, 6f, 0f); // mirrors web borderDash: [6, 3]

        LineData lineData = new LineData(actualSet, forecastSet);
        lineData.setValueTextColor(android.graphics.Color.WHITE);
        salesTrendData.postValue(lineData);
    }

    private void buildMonthlyPerformanceChart(List<SalesChartResponse.MonthlyPoint> monthly) {
        List<BarEntry> entries = new ArrayList<>();
        int i = 0;
        for (SalesChartResponse.MonthlyPoint pt : monthly) {
            entries.add(new BarEntry(i++, pt.revenue / 1000f));
        }

        // Color and label mirror web's Historical Performance bar chart:
        //   web backgroundColor: rgba(99,102,241,0.9) = indigo #6366f1
        //   web label: "Monthly Sales"
        BarDataSet barSet = new BarDataSet(entries, "Monthly Sales");
        barSet.setColor(android.graphics.Color.parseColor("#6366f1"));
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

    public LocalDate getCurrentStartDate()    { return currentStartDate; }
    public LocalDate getCurrentEndDate()      { return currentEndDate;   }
    public String    getCurrentPeriodLabel()  { return currentPeriodLabel; }
    public void      setCurrentPeriodLabel(String label) { this.currentPeriodLabel = label; }

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
