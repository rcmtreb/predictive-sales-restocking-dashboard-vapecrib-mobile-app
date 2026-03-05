package com.example.vapecrib.ui.forecast;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.network.ForecastApiRecord;
import com.example.vapecrib.network.PagedForecastResponse;
import com.example.vapecrib.network.PagedProductsResponse;
import com.example.vapecrib.network.PagedSalesResponse;
import com.example.vapecrib.network.ProductApiRecord;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.SaleApiRecord;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        bgExecutor.execute(this::refreshForecastData);
    }

    public void setSelectedProduct(String product) {
        currentProduct   = (product == null || product.isEmpty()) ? "All Products" : product;
        currentProductId = productIdMap.get(currentProduct); // null for "All Products"
        bgExecutor.execute(this::refreshForecastData);
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
        try {
            String fromDate = currentStartDate.toString();
            String toDate   = currentEndDate.toString();

            // ── Forecast rows ──────────────────────────────────────────────────
            Map<LocalDate, Float> forecastMap = new TreeMap<>();
            int page = 1;
            while (true) {
                Response<PagedForecastResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getForecast(fromDate, toDate, currentProductId, page, 100)
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

            // ── Actual sales rows ──────────────────────────────────────────────
            Map<LocalDate, Float> salesMap = new TreeMap<>();
            page = 1;
            while (true) {
                Response<PagedSalesResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getSales(fromDate, toDate, currentProductId, page, 100)
                        .execute();
                if (!resp.isSuccessful()) break;
                PagedSalesResponse body = resp.body();
                if (body == null || body.getData() == null) break;
                for (SaleApiRecord s : body.getData()) {
                    if (s.date == null) continue;
                    try {
                        // sale_date from Flask is a full ISO datetime — take date part only
                        LocalDate d = LocalDate.parse(s.date.substring(0, 10));
                        salesMap.merge(d, s.actualSales, Float::sum);
                    } catch (Exception ignored) {}
                }
                if (page >= body.getPages() || body.getPages() == 0) break;
                page++;
            }

            // ── Merge into chart entries ───────────────────────────────────────
            // Union of all dates from both maps
            Map<LocalDate, float[]> merged = new TreeMap<>();
            forecastMap.forEach((d, v) ->
                    merged.computeIfAbsent(d, k -> new float[2])[1] += v);
            salesMap.forEach((d, v) ->
                    merged.computeIfAbsent(d, k -> new float[2])[0] += v);

            if (merged.isEmpty()) {
                forecastData.postValue(null);
                forecastSummary.postValue("No data available for selected period.");
                selectedProductInfo.postValue(currentProduct.equals("All Products")
                        ? "Showing aggregated data" : "Product: " + currentProduct);
                return;
            }

            buildChartFromMerged(merged);

        } catch (Exception e) {
            apiError.postValue("Failed to load forecast: " + e.getMessage());
        }
    }

    private void buildChartFromMerged(Map<LocalDate, float[]> merged) {
        List<Entry> actualEntries   = new ArrayList<>();
        List<Entry> forecastEntries = new ArrayList<>();

        int step = Math.max(1, merged.size() / 60);
        int idx  = 0;
        float totalActual = 0, totalForecast = 0;

        List<float[]> valueList = new ArrayList<>(merged.values());
        for (int i = 0; i < valueList.size(); i += step) {
            float actual   = valueList.get(i)[0];
            float forecast = valueList.get(i)[1];
            actualEntries.add(new Entry(idx, actual));
            forecastEntries.add(new Entry(idx, forecast));
            idx++;
        }
        for (float[] v : valueList) { totalActual += v[0]; totalForecast += v[1]; }

        LineDataSet actualSet = new LineDataSet(actualEntries, "Actual");
        actualSet.setColor(android.graphics.Color.parseColor("#FF9800"));
        actualSet.setLineWidth(2f);
        actualSet.setDrawValues(false);
        actualSet.setDrawCircles(false);

        LineDataSet forecastSet = new LineDataSet(forecastEntries, "Forecast");
        forecastSet.setColor(android.graphics.Color.parseColor("#2196F3"));
        forecastSet.setLineWidth(2f);
        forecastSet.setDrawValues(false);
        forecastSet.setDrawCircles(false);
        forecastSet.enableDashedLine(10f, 5f, 0f);

        LineData lineData = new LineData(actualSet, forecastSet);
        lineData.setValueTextColor(android.graphics.Color.WHITE);
        forecastData.postValue(lineData);

        // Compute accuracy from actual vs forecast totals (same method as web dashboard)
        float accuracy = totalActual > 0
                ? Math.max(0, 100f - (Math.abs(totalActual - totalForecast) / totalActual) * 100f)
                : 0f;

        forecastSummary.postValue(String.format(
                "Period: %s to %s  |  Accuracy: %.1f%%\n" +
                "Total Actual: %.0f units  |  Total Forecast: %.0f units",
                currentStartDate, currentEndDate, accuracy, totalActual, totalForecast));

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
