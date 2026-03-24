package com.example.vapecrib.ui.forecast;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.network.DailyForecastResponse;
import com.example.vapecrib.network.PagedProductsResponse;
import com.example.vapecrib.network.ProductApiRecord;
import com.example.vapecrib.network.RetrofitClient;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** Day-name labels for the X-axis (e.g. "Mon\nMar 23"), matching the web chart. */
    private final MutableLiveData<List<String>> xAxisLabels         = new MutableLiveData<>();

    /** Maps product display-name → server product id. Null entry = "All Products". */
    private final Map<String, Integer> productIdMap = new LinkedHashMap<>();

    // Current filter state — mirrors the web's year / month / week selectors
    private int     currentYear      = LocalDate.now().getYear();
    private int     currentMonth     = LocalDate.now().getMonthValue();
    private int     currentWeek      = 0;   // 0 = let server pick current week
    private String  currentProduct   = "All Products";
    private Integer currentProductId = null;

    private final ExecutorService bgExecutor = Executors.newFixedThreadPool(2);

    public ForecastViewModel(Application application) {
        super(application);
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
    public LiveData<List<String>> getXAxisLabels()         { return xAxisLabels;         }

    /**
     * Called by ForecastFragment when Year / Month / Week dropdowns change.
     * week is 1-based (matching the web's week selector value).
     */
    public void filterByYearMonthWeek(int year, int month, int week) {
        currentYear  = year;
        currentMonth = month;
        currentWeek  = week;
        bgExecutor.execute(this::refreshForecastData);
    }

    public void setSelectedProduct(String product) {
        currentProduct   = (product == null || product.isEmpty()) ? "All Products" : product;
        currentProductId = productIdMap.get(currentProduct); // null for "All Products"
    }

    // ── Data loading ──────────────────────────────────────────────────────────

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
        refreshForecastData();
    }

    /**
     * Fetches the pre-processed daily forecast from /api/mobile/forecast/daily —
     * the same endpoint the web uses — and builds the chart.
     * Must be called on a background thread.
     */
    private void refreshForecastData() {
        // Snapshot mutable state to detect stale results
        final int     snapYear      = currentYear;
        final int     snapMonth     = currentMonth;
        final int     snapWeek      = currentWeek;
        final String  snapProduct   = currentProduct;
        final Integer snapProductId = currentProductId;

        isLoading.postValue(true);
        try {
            Integer weekParam = (snapWeek > 0) ? snapWeek : null;

            Response<DailyForecastResponse> resp = RetrofitClient
                    .getInstance(getApplication())
                    .getApi()
                    .getDailyForecast(snapYear, snapMonth, weekParam, snapProductId)
                    .execute();

            if (!resp.isSuccessful() || resp.body() == null || !resp.body().success) {
                String errMsg = "Server error (HTTP " + resp.code() + ")";
                if (resp.code() == 404) {
                    errMsg = "Forecast endpoint not found — server may need redeployment.";
                }
                apiError.postValue(errMsg);
                forecastData.postValue(new LineData());
                forecastSummary.postValue(errMsg);
                return;
            }

            DailyForecastResponse body = resp.body();

            // Stale-guard
            if (snapYear != currentYear || snapMonth != currentMonth
                    || snapWeek != currentWeek || !snapProduct.equals(currentProduct)) {
                return;
            }

            if ((body.actual == null  || body.actual.isEmpty())
             && (body.forecast == null || body.forecast.isEmpty())) {
                forecastData.postValue(new LineData());
                forecastSummary.postValue(
                        "No sales or forecast data for this period.\n"
                        + "Try a different week, or go to the web admin and run Refresh All Forecasts.");
                selectedProductInfo.postValue(snapProduct.equals("All Products")
                        ? "Showing aggregated data" : "Product: " + snapProduct);
                return;
            }

            buildChartFromResponse(body, snapProduct);

        } catch (Exception e) {
            apiError.postValue("Failed to load forecast: " + e.getMessage());
            forecastData.postValue(new LineData());
            forecastSummary.postValue("Connection error: " + e.getMessage());
        } finally {
            isLoading.postValue(false);
        }
    }

    /**
     * Builds the MPAndroidChart LineData from the DailyForecastResponse.
     *
     * Mirrors the web renderDailyForecastChart():
     *   - Green solid line  = actual[].sales    (historical days with data)
     *   - Purple dashed line= forecast[].sales  (ALL days in the week, including 0)
     * The web keeps 0-valued forecast points visible on the chart; we do the same.
     */
    private void buildChartFromResponse(DailyForecastResponse body, String productLabel) {
        // Collect all dates from both actual and forecast in sorted order.
        // Use a LinkedHashMap keyed on date so insertion order == sorted order
        // (server already returns both arrays sorted by date).
        java.util.TreeMap<String, float[]> merged = new java.util.TreeMap<>();

        // Track which dates come from actual[] and forecast[]
        java.util.Set<String> actualDates   = new java.util.HashSet<>();
        java.util.Set<String> forecastDates = new java.util.HashSet<>();

        // Map date → day_name for X-axis labels
        java.util.Map<String, String> dayNameMap = new java.util.LinkedHashMap<>();

        if (body.actual != null) {
            for (DailyForecastResponse.ActualPoint pt : body.actual) {
                merged.computeIfAbsent(pt.date, k -> new float[2])[0] = pt.sales;
                actualDates.add(pt.date);
                if (pt.dayName != null) dayNameMap.put(pt.date, pt.dayName);
            }
        }
        if (body.forecast != null) {
            for (DailyForecastResponse.ForecastPoint pt : body.forecast) {
                merged.computeIfAbsent(pt.date, k -> new float[2])[1] = pt.sales;
                forecastDates.add(pt.date);
                if (pt.dayName != null) dayNameMap.putIfAbsent(pt.date, pt.dayName);
            }
        }

        List<Entry> actualEntries   = new ArrayList<>();
        List<Entry> forecastEntries = new ArrayList<>();
        List<String> labels         = new ArrayList<>();
        float totalActual = 0, totalForecast = 0;
        int i = 0;

        for (Map.Entry<String, float[]> e : merged.entrySet()) {
            String dateStr = e.getKey();
            float actual   = e.getValue()[0];
            float forecast = e.getValue()[1];

            // Build X-axis label: "Mon\nMar 23" matching web's format
            String dayName = dayNameMap.get(dateStr);
            if (dayName != null && dateStr.length() >= 10) {
                String shortDay = dayName.length() > 3 ? dayName.substring(0, 3) : dayName;
                // dateStr = "2026-03-23" → month=03, day=23
                int mm = Integer.parseInt(dateStr.substring(5, 7));
                int dd = Integer.parseInt(dateStr.substring(8, 10));
                String[] months = {"","Jan","Feb","Mar","Apr","May","Jun",
                                   "Jul","Aug","Sep","Oct","Nov","Dec"};
                labels.add(shortDay + "\n" + months[mm] + " " + dd);
            } else {
                labels.add(dateStr.substring(5)); // fallback: "03-23"
            }

            // Actual line: include ALL dates the server returned in actual[],
            // even if sales=0 (matches web — the server gap-fills missing days with 0)
            if (actualDates.contains(dateStr)) {
                actualEntries.add(new Entry(i, actual));
                totalActual += actual;
            }

            // Forecast line: include ALL dates from forecast[], including 0 values.
            // The web chart renders every forecast point; skipping 0s caused the
            // mobile chart to look different or show "no data".
            if (forecastDates.contains(dateStr)) {
                forecastEntries.add(new Entry(i, forecast));
                totalForecast += forecast;
            }

            i++;
        }

        // Post labels for the Fragment's X-axis formatter
        xAxisLabels.postValue(labels);

        LineDataSet actualSet = new LineDataSet(actualEntries, "Actual (units)");
        actualSet.setColor(android.graphics.Color.parseColor("#34d399"));
        actualSet.setLineWidth(2f);
        actualSet.setDrawValues(false);
        actualSet.setDrawCircles(false);

        LineData lineData;
        if (forecastEntries.isEmpty()) {
            lineData = new LineData(actualSet);
            forecastSummary.postValue(
                    "No forecast data for this period.\n"
                    + "To generate forecasts, go to the web admin → Refresh All Forecasts.");
        } else {
            LineDataSet forecastSet = new LineDataSet(forecastEntries, "Forecast (units)");
            forecastSet.setColor(android.graphics.Color.parseColor("#818cf8"));
            forecastSet.setLineWidth(2f);
            forecastSet.setDrawValues(false);
            forecastSet.setDrawCircles(false);
            forecastSet.enableDashedLine(10f, 5f, 0f);

            lineData = new LineData(actualSet, forecastSet);

            float accuracy = totalActual > 0
                    ? Math.max(0, 100f - (Math.abs(totalActual - totalForecast) / totalActual) * 100f)
                    : 0f;
            forecastSummary.postValue(String.format(
                    "Forecast: %.0f units  |  Actual: %.0f units  |  Accuracy: %.1f%%",
                    totalForecast, totalActual, accuracy));
        }
        lineData.setValueTextColor(android.graphics.Color.WHITE);
        forecastData.postValue(lineData);

        selectedProductInfo.postValue(productLabel.equals("All Products")
                ? "Showing aggregated data for all products"
                : "Product: " + productLabel);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bgExecutor.shutdownNow();
    }
}
