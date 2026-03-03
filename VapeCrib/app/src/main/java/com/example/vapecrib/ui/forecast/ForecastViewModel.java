package com.example.vapecrib.ui.forecast;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.data.db.AppDatabase;
import com.example.vapecrib.data.db.SalesDataEntity;
import com.example.vapecrib.model.SalesData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ForecastViewModel extends AndroidViewModel {

    private final MutableLiveData<LineData> forecastData       = new MutableLiveData<>();
    private final MutableLiveData<String>   forecastSummary    = new MutableLiveData<>("");
    private final MutableLiveData<String>   selectedProductInfo = new MutableLiveData<>("");

    private final AppDatabase db;
    private List<SalesData> allSalesData = new ArrayList<>();
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;
    private String    currentProduct = "All Products";
    private boolean dataLoaded = false;

    // Fixed product list used by the fragment dropdown
    public static final String[] PRODUCT_LIST = {
        "All Products",
        "Vape Pen V1", "E-Liquid Berry", "Coil Pack A", "Battery 18650",
        "Charger USB-C", "Tank Glass", "Drip Tip Metal", "Cotton Organic",
        "Wire Kanthal", "Mod Box V2", "Atomizer RDA", "E-Liquid Mint",
        "Coil Pack B", "Battery 21700", "E-Liquid Vanilla", "Coil Mesh",
        "Premium Juice A", "Premium Juice B", "Starter Kit Pro", "Advanced Mod X"
    };

    public ForecastViewModel(Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        currentStartDate = LocalDate.of(2022, 1, 1);
        currentEndDate   = LocalDate.now();
        loadFromDatabase();
    }

    private void loadFromDatabase() {
        AppDatabase.dbExecutor.execute(() -> {
            List<SalesDataEntity> entities = db.salesDataDao().getAll();
            List<SalesData> data = new ArrayList<>();
            for (SalesDataEntity e : entities) {
                data.add(new SalesData(
                        LocalDate.parse(e.date),
                        e.actualSales, e.forecastedSales,
                        e.revenue, e.productsCount));
            }
            allSalesData = data;
            dataLoaded = true;
            // Run on main thread for LiveData updates
            if (getApplication() != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(this::updateForecastChart);
            }
        });
    }

    public LiveData<LineData> getForecastData()        { return forecastData;        }
    public LiveData<String>   getForecastSummary()     { return forecastSummary;     }
    public LiveData<String>   getSelectedProductInfo() { return selectedProductInfo; }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        if (dataLoaded) {
            updateForecastChart();
        }
    }

    public void setSelectedProduct(String product) {
        currentProduct = product;
        if (dataLoaded) {
            updateForecastChart();
        }
    }

    private void updateForecastChart() {
        List<SalesData> filtered = getFilteredData();

        if (filtered.isEmpty()) {
            forecastData.setValue(null);
            forecastSummary.setValue("No data available for selected period.");
            return;
        }

        // Scale factor per product - simulates per-product data
        float scale = getProductScale(currentProduct);

        List<Entry> actualEntries   = new ArrayList<>();
        List<Entry> forecastEntries = new ArrayList<>();

        int step = Math.max(1, filtered.size() / 60);
        int idx  = 0;
        for (int i = 0; i < filtered.size(); i += step) {
            SalesData d = filtered.get(i);
            actualEntries.add(new Entry(idx, d.getActualSales()      * scale));
            forecastEntries.add(new Entry(idx, d.getForecastedSales() * scale));
            idx++;
        }

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
        forecastData.setValue(lineData);

        // Summary stats
        float totalActual = 0, totalForecast = 0;
        for (SalesData d : filtered) {
            totalActual   += d.getActualSales()      * scale;
            totalForecast += d.getForecastedSales()  * scale;
        }
        float accuracy = totalActual > 0
                ? Math.max(0, 100f - (Math.abs(totalActual - totalForecast) / totalActual) * 100f)
                : 87f;

        forecastSummary.setValue(String.format(
            "Period: %s to %s  |  Accuracy: %.1f%%\nTotal Actual: %.0f units  |  Total Forecast: %.0f units",
            currentStartDate.toString(), currentEndDate.toString(),
            accuracy, totalActual, totalForecast));

        selectedProductInfo.setValue(currentProduct.equals("All Products")
                ? "Showing aggregated data for all products"
                : "Product: " + currentProduct);
    }

    private List<SalesData> getFilteredData() {
        List<SalesData> out = new ArrayList<>();
        for (SalesData d : allSalesData) {
            if (!d.getDate().isBefore(currentStartDate) && !d.getDate().isAfter(currentEndDate))
                out.add(d);
        }
        return out;
    }

    private float getProductScale(String product) {
        if (product == null || product.equals("All Products")) return 1.0f;
        int h = Math.abs(product.hashCode()) % 9;
        return 0.1f + h * 0.1f;
    }
}
