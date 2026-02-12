package com.example.vapecrib.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.LineData;

public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<String> totalSalesProducts = new MutableLiveData<>("0");
    private final MutableLiveData<String> totalSalesRevenue = new MutableLiveData<>("₱0.00");
    private final MutableLiveData<String> totalInventoryValue = new MutableLiveData<>("₱0.00");
    private final MutableLiveData<String> forecastAccuracy = new MutableLiveData<>("0%");
    private final MutableLiveData<String> activeAlerts = new MutableLiveData<>("0");
    private final MutableLiveData<String> expiringStocks = new MutableLiveData<>("0");
    private final MutableLiveData<String> criticalCount = new MutableLiveData<>("Critical: 0");
    private final MutableLiveData<String> highCount = new MutableLiveData<>("High: 0");
    private final MutableLiveData<String> mediumCount = new MutableLiveData<>("Medium: 0");
    private final MutableLiveData<LineData> salesTrendData = new MutableLiveData<>();
    private final MutableLiveData<BarData> monthlyPerformanceData = new MutableLiveData<>();

    public LiveData<String> getTotalSalesProducts() {
        return totalSalesProducts;
    }

    public LiveData<String> getTotalSalesRevenue() {
        return totalSalesRevenue;
    }

    public LiveData<String> getTotalInventoryValue() {
        return totalInventoryValue;
    }

    public LiveData<String> getForecastAccuracy() {
        return forecastAccuracy;
    }

    public LiveData<String> getActiveAlerts() {
        return activeAlerts;
    }

    public LiveData<String> getExpiringStocks() {
        return expiringStocks;
    }

    public LiveData<String> getCriticalCount() {
        return criticalCount;
    }

    public LiveData<String> getHighCount() {
        return highCount;
    }

    public LiveData<String> getMediumCount() {
        return mediumCount;
    }

    public LiveData<LineData> getSalesTrendData() {
        return salesTrendData;
    }

    public LiveData<BarData> getMonthlyPerformanceData() {
        return monthlyPerformanceData;
    }
}
