package com.example.vapecrib.ui.reports;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.vapecrib.data.SampleDataProvider;
import com.example.vapecrib.model.SalesData;

import java.time.LocalDate;
import java.util.List;

public class ReportsViewModel extends ViewModel {

    private final MutableLiveData<String> criticalCount  = new MutableLiveData<>();
    private final MutableLiveData<String> highCount      = new MutableLiveData<>();
    private final MutableLiveData<String> mediumCount    = new MutableLiveData<>();
    private final MutableLiveData<String> lowCount       = new MutableLiveData<>();
    private final MutableLiveData<String> expiringItems  = new MutableLiveData<>();
    private final MutableLiveData<String> totalRevenue   = new MutableLiveData<>();
    private final MutableLiveData<String> totalProducts  = new MutableLiveData<>();
    private final MutableLiveData<String> totalOrders    = new MutableLiveData<>();

    private final List<SalesData> allSalesData;
    private LocalDate currentStartDate;
    private LocalDate currentEndDate;

    public ReportsViewModel() {
        allSalesData = SampleDataProvider.generateSampleSalesData();
        // Default "all time" range based on CSV/sample coverage
        currentStartDate = LocalDate.of(2022, 1, 1);
        currentEndDate   = LocalDate.of(2026, 3, 31);
        loadData();
    }

    public void filterByDateRange(LocalDate start, LocalDate end) {
        currentStartDate = start;
        currentEndDate   = end;
        loadData();
    }

    private void loadData() {
        // Inventory counts are time-independent (current stock snapshot)
        criticalCount.setValue("Critical: "      + SampleDataProvider.getCriticalCount());
        highCount.setValue("High: "              + SampleDataProvider.getHighCount());
        mediumCount.setValue("Medium: "          + SampleDataProvider.getMediumCount());
        lowCount.setValue("Low: "                + SampleDataProvider.getLowCount());
        expiringItems.setValue("Expiring Soon: " + SampleDataProvider.getExpiringCount());

        // Revenue and product totals filtered by selected date range
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
        totalRevenue.setValue(String.format("P%,.0f", rev));
        totalProducts.setValue(String.valueOf(prod));
        totalOrders.setValue(String.valueOf(orders));
    }

    public LiveData<String> getCriticalCount()  { return criticalCount;  }
    public LiveData<String> getHighCount()      { return highCount;      }
    public LiveData<String> getMediumCount()    { return mediumCount;    }
    public LiveData<String> getLowCount()       { return lowCount;       }
    public LiveData<String> getExpiringItems()  { return expiringItems;  }
    public LiveData<String> getTotalRevenue()   { return totalRevenue;   }
    public LiveData<String> getTotalProducts()  { return totalProducts;  }
    public LiveData<String> getTotalOrders()    { return totalOrders;    }
}

