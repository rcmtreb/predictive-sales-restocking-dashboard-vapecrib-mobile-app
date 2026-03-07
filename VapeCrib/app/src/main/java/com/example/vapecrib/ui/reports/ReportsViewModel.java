package com.example.vapecrib.ui.reports;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vapecrib.network.ImportHistoryResponse;
import com.example.vapecrib.network.InventoryReportResponse;
import com.example.vapecrib.network.RetrofitClient;
import com.example.vapecrib.network.SalesReportResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class ReportsViewModel extends AndroidViewModel {

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    // Sales Report
    private final MutableLiveData<List<SalesReportResponse.SaleItem>> salesItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> salesSummary = new MutableLiveData<>("Loading...");
    private final MutableLiveData<Boolean> salesLoading = new MutableLiveData<>(false);

    // Inventory Report
    private final MutableLiveData<List<InventoryReportResponse.InventoryItem>> inventoryItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> inventorySummary = new MutableLiveData<>("Loading...");
    private final MutableLiveData<Boolean> inventoryLoading = new MutableLiveData<>(false);

    // Import History
    private final MutableLiveData<List<ImportHistoryResponse.ImportItem>> importItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> importSummary = new MutableLiveData<>("Loading...");
    private final MutableLiveData<Boolean> importLoading = new MutableLiveData<>(false);

    // Error
    private final MutableLiveData<String> apiError = new MutableLiveData<>();

    public ReportsViewModel(Application application) {
        super(application);
        fetchSalesReport("30d");
        fetchInventoryReport("current");
        fetchImportHistory("10");
    }

    public void fetchSalesReport(String period) {
        salesLoading.postValue(true);
        executor.execute(() -> {
            try {
                Response<SalesReportResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getSalesReport(period)
                        .execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().data != null) {
                    SalesReportResponse.Data data = resp.body().data;
                    salesItems.postValue(data.sales != null ? data.sales : new ArrayList<>());
                    salesSummary.postValue(String.format("%s  |  %d sales  |  P%,.2f revenue",
                            data.periodLabel, data.totalSales, data.totalRevenue));
                } else {
                    salesSummary.postValue("Failed to load sales report");
                }
            } catch (Exception e) {
                apiError.postValue("Sales report: " + e.getMessage());
                salesSummary.postValue("Error loading data");
            }
            salesLoading.postValue(false);
        });
    }

    public void fetchInventoryReport(String type) {
        inventoryLoading.postValue(true);
        executor.execute(() -> {
            try {
                Response<InventoryReportResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getInventoryReport(type)
                        .execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().data != null) {
                    InventoryReportResponse.Data data = resp.body().data;
                    inventoryItems.postValue(data.inventory != null ? data.inventory : new ArrayList<>());
                    inventorySummary.postValue(String.format("%s  |  %d items  |  P%,.2f value",
                            data.reportLabel, data.totalItems, data.totalValue));
                } else {
                    inventorySummary.postValue("Failed to load inventory report");
                }
            } catch (Exception e) {
                apiError.postValue("Inventory report: " + e.getMessage());
                inventorySummary.postValue("Error loading data");
            }
            inventoryLoading.postValue(false);
        });
    }

    public void fetchImportHistory(String limit) {
        importLoading.postValue(true);
        executor.execute(() -> {
            try {
                Response<ImportHistoryResponse> resp = RetrofitClient
                        .getInstance(getApplication())
                        .getApi()
                        .getImportHistory(limit)
                        .execute();

                if (resp.isSuccessful() && resp.body() != null && resp.body().data != null) {
                    ImportHistoryResponse.Data data = resp.body().data;
                    importItems.postValue(data.imports != null ? data.imports : new ArrayList<>());
                    importSummary.postValue(String.format("%d imports loaded", data.total));
                } else {
                    importSummary.postValue("Failed to load import history");
                }
            } catch (Exception e) {
                apiError.postValue("Import history: " + e.getMessage());
                importSummary.postValue("Error loading data");
            }
            importLoading.postValue(false);
        });
    }

    public LiveData<List<SalesReportResponse.SaleItem>> getSalesItems()    { return salesItems; }
    public LiveData<String> getSalesSummary()                               { return salesSummary; }
    public LiveData<Boolean> getSalesLoading()                              { return salesLoading; }

    public LiveData<List<InventoryReportResponse.InventoryItem>> getInventoryItems() { return inventoryItems; }
    public LiveData<String> getInventorySummary()                           { return inventorySummary; }
    public LiveData<Boolean> getInventoryLoading()                          { return inventoryLoading; }

    public LiveData<List<ImportHistoryResponse.ImportItem>> getImportItems() { return importItems; }
    public LiveData<String> getImportSummary()                              { return importSummary; }
    public LiveData<Boolean> getImportLoading()                             { return importLoading; }

    public LiveData<String> getApiError()                                   { return apiError; }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
