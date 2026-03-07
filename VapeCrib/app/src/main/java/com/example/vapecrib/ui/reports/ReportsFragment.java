package com.example.vapecrib.ui.reports;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vapecrib.databinding.FragmentReportsBinding;

public class ReportsFragment extends Fragment {

    private ReportsViewModel viewModel;
    private FragmentReportsBinding binding;

    private SalesReportAdapter salesAdapter;
    private InventoryReportAdapter inventoryAdapter;
    private ImportHistoryAdapter importAdapter;

    // Dropdown labels  API parameter values
    private static final String[] SALES_LABELS   = {"Last 7 Days", "Last 30 Days", "Last 90 Days", "This Year", "All Time"};
    private static final String[] SALES_VALUES   = {"7d",          "30d",          "90d",          "1y",        "all"};

    private static final String[] INV_LABELS     = {"Current Stock Levels", "Low Stock Items", "All Inventory"};
    private static final String[] INV_VALUES     = {"current",              "low",              "all"};

    private static final String[] IMPORT_LABELS  = {"Last 10 Imports", "Last 25 Imports", "Last 50 Imports", "All Imports"};
    private static final String[] IMPORT_VALUES  = {"10",              "25",               "50",              "all"};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(ReportsViewModel.class);
        binding = FragmentReportsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerViews();
        setupDropdowns();
        observeData();

        // Pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.fetchSalesReport("30d");
            viewModel.fetchInventoryReport("current");
            viewModel.fetchImportHistory("10");
            binding.spinnerSalesPeriod.setText(SALES_LABELS[1], false);
            binding.spinnerInventoryType.setText(INV_LABELS[0], false);
            binding.spinnerImportLimit.setText(IMPORT_LABELS[0], false);
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void setupRecyclerViews() {
        salesAdapter = new SalesReportAdapter();
        binding.rvSales.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSales.setAdapter(salesAdapter);

        inventoryAdapter = new InventoryReportAdapter();
        binding.rvInventory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvInventory.setAdapter(inventoryAdapter);

        importAdapter = new ImportHistoryAdapter();
        binding.rvImports.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvImports.setAdapter(importAdapter);
    }

    private void setupDropdowns() {
        // Sales period dropdown
        ArrayAdapter<String> salesDropdown = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, SALES_LABELS);
        binding.spinnerSalesPeriod.setAdapter(salesDropdown);
        binding.spinnerSalesPeriod.setText(SALES_LABELS[1], false);  // default: Last 30 Days
        binding.spinnerSalesPeriod.setOnItemClickListener((parent, view, position, id) ->
                viewModel.fetchSalesReport(SALES_VALUES[position]));

        // Inventory type dropdown
        ArrayAdapter<String> invDropdown = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, INV_LABELS);
        binding.spinnerInventoryType.setAdapter(invDropdown);
        binding.spinnerInventoryType.setText(INV_LABELS[0], false);  // default: Current Stock
        binding.spinnerInventoryType.setOnItemClickListener((parent, view, position, id) ->
                viewModel.fetchInventoryReport(INV_VALUES[position]));

        // Import limit dropdown
        ArrayAdapter<String> impDropdown = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, IMPORT_LABELS);
        binding.spinnerImportLimit.setAdapter(impDropdown);
        binding.spinnerImportLimit.setText(IMPORT_LABELS[0], false);  // default: Last 10
        binding.spinnerImportLimit.setOnItemClickListener((parent, view, position, id) ->
                viewModel.fetchImportHistory(IMPORT_VALUES[position]));
    }

    private void observeData() {
        // Sales
        viewModel.getSalesItems().observe(getViewLifecycleOwner(), salesAdapter::setItems);
        viewModel.getSalesSummary().observe(getViewLifecycleOwner(), binding.tvSalesSummary::setText);
        viewModel.getSalesLoading().observe(getViewLifecycleOwner(), loading ->
                binding.pbSales.setVisibility(loading ? View.VISIBLE : View.GONE));

        // Inventory
        viewModel.getInventoryItems().observe(getViewLifecycleOwner(), inventoryAdapter::setItems);
        viewModel.getInventorySummary().observe(getViewLifecycleOwner(), binding.tvInventorySummary::setText);
        viewModel.getInventoryLoading().observe(getViewLifecycleOwner(), loading ->
                binding.pbInventory.setVisibility(loading ? View.VISIBLE : View.GONE));

        // Imports
        viewModel.getImportItems().observe(getViewLifecycleOwner(), importAdapter::setItems);
        viewModel.getImportSummary().observe(getViewLifecycleOwner(), binding.tvImportSummary::setText);
        viewModel.getImportLoading().observe(getViewLifecycleOwner(), loading ->
                binding.pbImports.setVisibility(loading ? View.VISIBLE : View.GONE));

        // Errors
        viewModel.getApiError().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
