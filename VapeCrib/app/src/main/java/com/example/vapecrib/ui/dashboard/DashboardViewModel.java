package com.example.vapecrib.ui.dashboard;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.data.db.AppDatabase;
import com.example.vapecrib.data.db.SalesDataEntity;
import com.example.vapecrib.model.SalesData;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final AppDatabase db;
    private List<SalesData> allSalesData = new ArrayList<>();
    private int cachedCritical, cachedHigh, cachedMedium, cachedExpiring;
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

            computeAndPost();
        });
    }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        if (dataLoaded) {
            AppDatabase.dbExecutor.execute(this::computeAndPost);
        }
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

        float accuracy = totalActual > 0
                ? Math.max(0, 100f - (Math.abs(totalActual - totalForecast) / totalActual) * 100f)
                : 87f;

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
