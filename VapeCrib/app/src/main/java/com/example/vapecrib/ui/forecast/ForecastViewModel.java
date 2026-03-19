package com.example.vapecrib.ui.forecast;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.network.ForecastApiRecord;
import com.example.vapecrib.network.PagedForecastResponse;
import com.example.vapecrib.network.PagedProductsResponse;
import com.example.vapecrib.network.ProductApiRecord;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.SalesChartResponse;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ForecastViewModel extends AndroidViewModel {

    private final MutableLiveData<LineData>     forecastData        = new MutableLiveData<>();
    private final MutableLiveData<String>       forecastSummary     = new MutableLiveData<>("");
    private final MutableLiveData<String>       selectedProductInfo = new MutableLiveData<>("");
    /** Product names for the dropdown — starts with just "All Products", fills after API call. */
    private final MutableLiveData<List<String>> productNames        = new MutableLiveData<>();
    private final MutableLiveData<String>       apiError            = new MutableLiveData<>();
    /** True while a background fetch is in-flight; drives the SwipeRefreshLayout indicator. */
    private final MutableLiveData<Boolean>      isLoading           = new MutableLiveData<>(false);

    /** Maps product display-name → server product id. Null entry = "All Products". */
    private final Map<String, Integer> productIdMap = new LinkedHashMap<>();

    private LocalDate currentStartDate = LocalDate.of(2022, 1, 1);
    private LocalDate currentEndDate   = LocalDate.now();
    private String    currentProduct   = "All Products";
    /** Null means "all products"; set when a specific product is chosen. */
    private Integer   currentProductId = null;

    private final ExecutorService bgExecutor = Executors.newFixedThreadPool(2);

    // ── No longer used: static fake product list and Room DB loading ──────────
    // (removed PRODUCT_LIST and SalesDataEntity loading)

    public ForecastViewModel(Application application) {
        super(application);
        // Seed dropdown immediately with "All Products", then load real names
        List<String> initial = new ArrayList<>();
        initial.add("All Products");
        productNames.setValue(initial);
        bgExecutor.execute(this::loadProductsAndForecast);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<LineData>     getForecastData()        { return forecastData;        }
    public LiveData<String>       getForecastSummary()     { return forecastSummary;     }
    public LiveData<String>       getSelectedProductInfo() { return selectedProductInfo; }
    public LiveData<List<String>> getProductNames()        { return productNames;        }
    public LiveData<String>       getApiError()            { return apiError;            }
    public LiveData<Boolean>      getIsLoading()           { return isLoading;           }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        bgExecutor.execute(this::refreshForecastData);
    }

    public void setSelectedProduct(String product) {
        currentProduct   = (product == null || product.isEmpty()) ? "All Products" : product;
        currentProductId = productIdMap.get(currentProduct); // null for "All Products"
        // Do NOT trigger a fetch here. filterByDateRange() is always called right
        // after on the same main thread, so let it do the single combined fetch.
        // Triggering here would create a second concurrent task using STALE dates
        // (set before filterByDateRange runs), which races against the correct task
        // and can post null/empty, wiping the forecast line.
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Called once at startup. Fetches all product names from the server
     * (populates the dropdown), then loads the initial forecast chart.
     */
    private void loadProductsAndForecast() {
        try {
            List<String> names = new ArrayList<>();
            names.add("All Products");

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
                    names.add(p.name);
                    productIdMap.put(p.name, p.id);
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }
            productNames.postValue(names);
        } catch (Exception e) {
            // Keep the "All Products" fallback already posted in the constructor
        }
        // Load initial chart regardless of whether the product list succeeded
        refreshForecastData();
    }

    /**
     * Fetches forecast and sales data for the current product / date range and
     * builds a line chart. Must be called on a background thread.
     */
    private void refreshForecastData() {
        // Snapshot mutable state immediately so we can detect if the user changed
        // filters while this fetch was in-flight and discard stale results.
        final LocalDate snapStart     = currentStartDate;
        final LocalDate snapEnd       = currentEndDate;
        final String    snapProduct   = currentProduct;
        final Integer   snapProductId = currentProductId;

        isLoading.postValue(true);
        try {
            String fromDate = snapStart.toString();
            String toDate   = snapEnd.toString();

            // ── Forecast rows ──────────────────────────────────────────────────
            Map<LocalDate, Float> forecastMap = new TreeMap<>();
            int page = 1;
            while (true) {
                Response<PagedForecastResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getForecast(fromDate, toDate, snapProductId, page, 100)
                        .execute();
                if (!resp.isSuccessful()) break;
                PagedForecastResponse body = resp.body();
                if (body == null || body.getData() == null) break;
                for (ForecastApiRecord f : body.getData()) {
                    String dateStr = (f.periodKey != null) ? f.periodKey
                            : (f.forecastDate != null ? f.forecastDate.substring(0, 10) : null);
                    if (dateStr == null) continue;
                    try {
                        LocalDate d = LocalDate.parse(dateStr);
                        forecastMap.merge(d, f.predictedQuantity, Float::sum);
                    } catch (Exception ignored) {}
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }

            // ── Actual sales rows: use pre-aggregated chart data ───────────────────
            // Mirrors DashboardViewModel — one request instead of many paginated
            // transaction pages. The chart endpoint returns daily quantity totals
            // which is exactly what the forecast chart needs.
            Map<LocalDate, Float> salesMap = new TreeMap<>();
            try {
                Response<SalesChartResponse> chartResp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getSalesChart(fromDate, toDate, snapProductId)
                        .execute();
                if (chartResp.isSuccessful() && chartResp.body() != null
                        && chartResp.body().data != null
                        && chartResp.body().data.daily != null) {
                    for (SalesChartResponse.DailyPoint pt : chartResp.body().data.daily) {
                        if (pt.date == null) continue;
                        try {
                            LocalDate d = LocalDate.parse(pt.date);
                            salesMap.put(d, (float) pt.quantity);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            // ── Merge into chart entries ───────────────────────────────────────
            // Union of all dates from both maps
            Map<LocalDate, float[]> merged = new TreeMap<>();
            forecastMap.forEach((d, v) ->
                    merged.computeIfAbsent(d, k -> new float[2])[1] += v);
            salesMap.forEach((d, v) ->
                    merged.computeIfAbsent(d, k -> new float[2])[0] += v);

            // Stale-guard: if the user changed filters while this fetch was running,
            // discard the result — the newer task will post its own correct data.
            if (!snapStart.equals(currentStartDate) || !snapEnd.equals(currentEndDate)
                    || !snapProduct.equals(currentProduct)) {
                return;
            }

            if (merged.isEmpty()) {
                // Post an empty-but-non-null LineData so the onCreateView null-guard
                // does NOT re-trigger another fetch every time the user navigates back.
                // (null would cause an infinite fetch→null→navigate back→fetch loop.)
                forecastData.postValue(new LineData());
                forecastSummary.postValue("No sales or forecast data for this period.\nTry a different week, or go to the web admin and run Refresh All Forecasts.");
                selectedProductInfo.postValue(currentProduct.equals("All Products")
                        ? "Showing aggregated data" : "Product: " + currentProduct);
                return;
            }

            buildChartFromMerged(merged, new HashSet<>(salesMap.keySet()));

        } catch (Exception e) {
            apiError.postValue("Failed to load forecast: " + e.getMessage());
        } finally {
            isLoading.postValue(false);
        }
    }

    /**
     * Builds chart entries following the same 2-variable pattern as the web's
     * renderDailyForecastChart: actual line only covers historical dates with real
     * sales data; forecast line covers ALL dates (historical comparison + future
     * predictions). Missing data points are omitted rather than plotted as 0,
     * which creates a natural gap/stop just as the web uses null values.
     */
    private void buildChartFromMerged(Map<LocalDate, float[]> merged, Set<LocalDate> actualDates) {
        List<Entry> actualEntries   = new ArrayList<>();
        List<Entry> forecastEntries = new ArrayList<>();

        int plotIdx  = 0;
        float totalActual = 0, totalForecast = 0;

        for (Map.Entry<LocalDate, float[]> e : merged.entrySet()) {
            LocalDate date   = e.getKey();
            float actual     = e.getValue()[0];
            float forecast   = e.getValue()[1];

            // Mirror web: actual line only for dates with real historical sales.
            // Future-only forecast dates have no entry here, so the actual line
            // stops naturally at the last day with real data (no artificial 0 drop).
            if (actualDates.contains(date)) {
                actualEntries.add(new Entry(plotIdx, actual));
                totalActual += actual;
            }

            // Forecast line covers both historical comparison and future predictions.
            // Only skip if there is genuinely no forecast value for this date.
            if (forecast > 0) {
                forecastEntries.add(new Entry(plotIdx, forecast));
                totalForecast += forecast;
            }

            plotIdx++;
        }

        // Colors match the web app's renderDailyForecastChart:
        //   Actual (units)   → green  #34d399
        //   Forecast (units) → purple #818cf8  (dashed, same as web)
        LineDataSet actualSet = new LineDataSet(actualEntries, "Actual (units)");
        actualSet.setColor(android.graphics.Color.parseColor("#34d399"));
        actualSet.setLineWidth(2f);
        actualSet.setDrawValues(false);
        actualSet.setDrawCircles(false);

        LineData lineData;
        if (forecastEntries.isEmpty()) {
            // No forecast rows in DB for this period — only render actual sales line.
            // Omit the forecast dataset entirely so a ghost legend entry with no
            // line doesn't confuse the user.
            lineData = new LineData(actualSet);
            forecastSummary.postValue(
                    "No forecast data for this period.\nTo generate forecasts, go to the web admin → Refresh All Forecasts.");
        } else {
            LineDataSet forecastSet = new LineDataSet(forecastEntries, "Forecast (units)");
            forecastSet.setColor(android.graphics.Color.parseColor("#818cf8"));
            forecastSet.setLineWidth(2f);
            forecastSet.setDrawValues(false);
            forecastSet.setDrawCircles(false);
            forecastSet.enableDashedLine(10f, 5f, 0f);

            lineData = new LineData(actualSet, forecastSet);

            // Compute accuracy from actual vs forecast totals (same method as web dashboard)
            float accuracy = totalActual > 0
                    ? Math.max(0, 100f - (Math.abs(totalActual - totalForecast) / totalActual) * 100f)
                    : 0f;
            forecastSummary.postValue(String.format(
                    "Forecast: %.0f units  |  Actual: %.0f units  |  Accuracy: %.1f%%",
                    totalForecast, totalActual, accuracy));
        }
        lineData.setValueTextColor(android.graphics.Color.WHITE);
        forecastData.postValue(lineData);

        selectedProductInfo.postValue(currentProduct.equals("All Products")
                ? "Showing aggregated data for all products"
                : "Product: " + currentProduct);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bgExecutor.shutdownNow();
    }
}
