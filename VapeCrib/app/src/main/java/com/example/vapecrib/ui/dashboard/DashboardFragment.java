package com.example.vapecrib.ui.dashboard;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.vapecrib.R;
import com.example.vapecrib.databinding.FragmentDashboardBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

@SuppressLint("NewApi")
public class DashboardFragment extends Fragment {

    private static final LocalDate CSV_MIN_DATE = LocalDate.of(2022, 1, 1);
    private static final LocalDate CSV_MAX_DATE = LocalDate.of(2026, 3, 31);

    private DashboardViewModel dashboardViewModel;
    private FragmentDashboardBinding binding;
    private LocalDate selectedStartDate = null;
    private LocalDate selectedEndDate = null;

    @SuppressLint("NewApi")
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Dates start null — no pre-fill, show all data immediately

        // Configure charts
        configureLineChart();
        configureBarChart();

        // KPI Observers
        dashboardViewModel.getTotalSalesProducts().observe(getViewLifecycleOwner(), binding.tvTotalSalesProducts::setText);
        dashboardViewModel.getTotalSalesRevenue().observe(getViewLifecycleOwner(), binding.tvTotalSalesRevenue::setText);
        dashboardViewModel.getTotalInventoryValue().observe(getViewLifecycleOwner(), binding.tvTotalInventoryValue::setText);
        dashboardViewModel.getForecastAccuracy().observe(getViewLifecycleOwner(), binding.tvForecastAccuracy::setText);
        dashboardViewModel.getActiveAlerts().observe(getViewLifecycleOwner(), binding.tvActiveAlerts::setText);
        dashboardViewModel.getExpiringStocks().observe(getViewLifecycleOwner(), binding.tvExpiringStocks::setText);
        dashboardViewModel.getCriticalCount().observe(getViewLifecycleOwner(), binding.tvCriticalCount::setText);
        dashboardViewModel.getHighCount().observe(getViewLifecycleOwner(), binding.tvHighCount::setText);
        dashboardViewModel.getMediumCount().observe(getViewLifecycleOwner(), binding.tvMediumCount::setText);
        dashboardViewModel.getSalesTrendData().observe(getViewLifecycleOwner(), lineData -> {
            if (lineData != null) {
                binding.lineChart.setData(lineData);
                binding.lineChart.getXAxis().setAxisMinimum(0);
                int entryCount = lineData.getDataSetByIndex(0).getEntryCount();
                binding.lineChart.getXAxis().setAxisMaximum(entryCount - 1);
                updateChartVisibility();
                binding.lineChart.animateX(300);
            }
        });
        dashboardViewModel.getMonthlyPerformanceData().observe(getViewLifecycleOwner(), barData -> {
            if (barData != null) {
                binding.barChart.setData(barData);
                binding.barChart.getXAxis().setAxisMinimum(0);
                int entryCount = barData.getDataSetByIndex(0).getEntryCount();
                binding.barChart.getXAxis().setAxisMaximum(entryCount - 1);
                binding.barChart.animateY(300);
            }
        });

        // Time Period Dropdown — preset options only, Start/End serve as custom range
        String[] timePeriods = {"All Time", "Last 30 Days", "Last 90 Days", "Last 6 Months", "Last Year"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, timePeriods);
        binding.actvTimePeriod.setAdapter(adapter);
        binding.actvTimePeriod.setOnItemClickListener((parent, view, position, id) -> {
            handleTimePeriodSelection(position);
        });

        // Date Click Listeners
        binding.etStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        binding.etEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // Checkbox Listeners
        binding.cbActual.setOnCheckedChangeListener((buttonView, isChecked) -> updateChartVisibility());
        binding.cbForecast.setOnCheckedChangeListener((buttonView, isChecked) -> updateChartVisibility());

        // Pull-to-refresh — reload all data without any date filter
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            selectedStartDate = null;
            selectedEndDate = null;
            binding.etStartDate.setText("");
            binding.etEndDate.setText("");
            binding.actvTimePeriod.setText("", false);
            dashboardViewModel.filterByDateRange(CSV_MIN_DATE, CSV_MAX_DATE);
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void handleTimePeriodSelection(int position) {
        LocalDate today = LocalDate.now();
        LocalDate periodStart;
        LocalDate periodEnd;
        switch (position) {
            case 0: // All Time — full CSV data range
                periodStart = CSV_MIN_DATE;
                periodEnd = CSV_MAX_DATE;
                break;
            case 1: // Last 30 Days
                periodStart = today.minusDays(30);
                periodEnd = today;
                break;
            case 2: // Last 90 Days
                periodStart = today.minusDays(90);
                periodEnd = today;
                break;
            case 3: // Last 6 Months
                periodStart = today.minusMonths(6);
                periodEnd = today;
                break;
            case 4: // Last Year
                periodStart = today.minusYears(1);
                periodEnd = today;
                break;
            default: return;
        }

        selectedStartDate = null;
        selectedEndDate = null;
        binding.etStartDate.setText("");
        binding.etEndDate.setText("");

        dashboardViewModel.filterByDateRange(periodStart, periodEnd);
    }

    private void updateDateDisplay() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        binding.etStartDate.setText(selectedStartDate.format(formatter));
        binding.etEndDate.setText(selectedEndDate.format(formatter));
    }

    private void showDatePickerDialog(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), (view, year1, month1, dayOfMonth) -> {
            LocalDate selectedDate = LocalDate.of(year1, month1 + 1, dayOfMonth);
            if (isStartDate) {
                selectedStartDate = selectedDate;
            } else {
                selectedEndDate = selectedDate;
            }
            binding.actvTimePeriod.setText("", false);
            updateDateDisplay();
            applyFilter();
        }, year, month, day);
        datePickerDialog.show();
    }

    private void applyFilter() {
        if (selectedStartDate == null || selectedEndDate == null) return;
        if (selectedEndDate.isBefore(selectedStartDate)) {
            // Swap dates if end date is before start date
            LocalDate temp = selectedStartDate;
            selectedStartDate = selectedEndDate;
            selectedEndDate = temp;
            updateDateDisplay();
        }
        dashboardViewModel.filterByDateRange(selectedStartDate, selectedEndDate);
    }

    private void updateChartVisibility() {
        LineData lineData = binding.lineChart.getLineData();
        if (lineData != null) {
            boolean isActualChecked = binding.cbActual.isChecked();
            boolean isForecastChecked = binding.cbForecast.isChecked();

            if (lineData.getDataSetCount() > 0) {
                lineData.getDataSetByIndex(0).setVisible(isActualChecked);
            }
            if (lineData.getDataSetCount() > 1) {
                lineData.getDataSetByIndex(1).setVisible(isForecastChecked);
            }
            binding.lineChart.invalidate();
        }
    }

    private void configureLineChart() {
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.setTouchEnabled(true);
        binding.lineChart.setDragEnabled(true);
        binding.lineChart.setScaleEnabled(true);
        binding.lineChart.setPinchZoom(true);
        binding.lineChart.getLegend().setEnabled(true);
        binding.lineChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        binding.lineChart.getAxisRight().setEnabled(false);
        
        binding.lineChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        binding.lineChart.getAxisLeft().setAxisMinimum(0f);
        
        binding.lineChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        binding.lineChart.getXAxis().setDrawGridLines(false);
        binding.lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    private void configureBarChart() {
        binding.barChart.getDescription().setEnabled(false);
        binding.barChart.setTouchEnabled(true);
        binding.barChart.setDragEnabled(true);
        binding.barChart.setScaleEnabled(true);
        binding.barChart.setPinchZoom(true);
        binding.barChart.getLegend().setEnabled(true);
        binding.barChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        binding.barChart.getAxisRight().setEnabled(false);
        
        binding.barChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        binding.barChart.getAxisLeft().setAxisMinimum(0f);
        
        binding.barChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        binding.barChart.getXAxis().setDrawGridLines(false);
        binding.barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
