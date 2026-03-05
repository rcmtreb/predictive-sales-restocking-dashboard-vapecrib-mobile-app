package com.example.vapecrib.ui.dashboard;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.vapecrib.R;
import com.example.vapecrib.databinding.FragmentDashboardBinding;
import com.example.vapecrib.ui.settings.SettingsFragment;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

@SuppressLint("NewApi")
public class DashboardFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private FragmentDashboardBinding binding;
    private LocalDate selectedStartDate;
    private LocalDate selectedEndDate;

    @SuppressLint("NewApi")
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize dates for filter logic (fields left empty — data shows All Time by default)
        selectedStartDate = LocalDate.of(2022, 1, 1);
        selectedEndDate = LocalDate.of(2026, 3, 31);

        // Apply saved display preferences
        applyDisplayPreferences();

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
        // Show a brief toast when the API can't be reached (cached data is still shown)
        dashboardViewModel.getApiError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty() && isAdded())
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
        });
        dashboardViewModel.getSalesTrendData().observe(getViewLifecycleOwner(), lineData -> {
            if (lineData != null) {
                binding.lineChart.setData(lineData);
                binding.lineChart.getXAxis().setAxisMinimum(0);
                int entryCount = lineData.getDataSetByIndex(0).getEntryCount();
                binding.lineChart.getXAxis().setAxisMaximum(entryCount - 1);
                updateChartVisibility();
                SharedPreferences p = requireContext()
                    .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                if (p.getBoolean(SettingsFragment.KEY_CHART_ANIM, true)) {
                    binding.lineChart.animateX(300);
                } else {
                    binding.lineChart.invalidate();
                }
            }
        });
        dashboardViewModel.getMonthlyPerformanceData().observe(getViewLifecycleOwner(), barData -> {
            if (barData != null) {
                binding.barChart.setData(barData);
                binding.barChart.getXAxis().setAxisMinimum(0);
                int entryCount = barData.getDataSetByIndex(0).getEntryCount();
                binding.barChart.getXAxis().setAxisMaximum(entryCount - 1);
                SharedPreferences p = requireContext()
                    .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);
                if (p.getBoolean(SettingsFragment.KEY_CHART_ANIM, true)) {
                    binding.barChart.animateY(300);
                } else {
                    binding.barChart.invalidate();
                }
            }
        });

        // Time Period Dropdown
        String[] timePeriods = {"All Time", "Last 30 Days", "Last 90 Days", "Last 6 Months", "Last Year", "Custom"};
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

        return root;
    }

    private void handleTimePeriodSelection(int position) {
        LocalDate today = LocalDate.now();
        
        switch (position) {
            case 0: // All Time
                selectedStartDate = LocalDate.of(2022, 1, 1);
                selectedEndDate = today;
                break;
            case 1: // Last 30 Days
                selectedStartDate = today.minusDays(30);
                selectedEndDate = today;
                break;
            case 2: // Last 90 Days
                selectedStartDate = today.minusDays(90);
                selectedEndDate = today;
                break;
            case 3: // Last 6 Months
                selectedStartDate = today.minusMonths(6);
                selectedEndDate = today;
                break;
            case 4: // Last Year
                selectedStartDate = today.minusYears(1);
                selectedEndDate = today;
                break;
            case 5: // Custom
                return; // User will select dates manually
        }
        
        updateDateDisplay();
        applyFilter();
    }

    private void updateDateDisplay() {
        if (binding == null) return;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        if (selectedStartDate != null) binding.etStartDate.setText(selectedStartDate.format(formatter));
        if (selectedEndDate   != null) binding.etEndDate.setText(selectedEndDate.format(formatter));
    }

    private void showDatePickerDialog(boolean isStartDate) {
        if (!isAdded() || getContext() == null) return;
        // Seed the picker from the currently selected date so the calendar opens at the right month
        LocalDate seed = isStartDate ? selectedStartDate : selectedEndDate;
        Calendar calendar = Calendar.getInstance();
        calendar.set(seed.getYear(), seed.getMonthValue() - 1, seed.getDayOfMonth());
        int year  = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day   = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), (view, year1, month1, dayOfMonth) -> {
            if (binding == null) return;   // fragment already destroyed — ignore
            LocalDate selectedDate = LocalDate.of(year1, month1 + 1, dayOfMonth);
            if (isStartDate) {
                selectedStartDate = selectedDate;
            } else {
                selectedEndDate = selectedDate;
            }
            updateDateDisplay();
            applyFilter();
        }, year, month, day);
        datePickerDialog.show();
    }

    private static final LocalDate DB_MIN_DATE = LocalDate.of(2022, 1, 1);
    private static final LocalDate DB_MAX_DATE = LocalDate.of(2026, 3, 31);

    private void applyFilter() {
        if (selectedStartDate == null || selectedEndDate == null) return;
        if (selectedEndDate.isBefore(selectedStartDate)) {
            // Swap dates if end date is before start date
            LocalDate temp    = selectedStartDate;
            selectedStartDate = selectedEndDate;
            selectedEndDate   = temp;
            updateDateDisplay();
        }
        // Clamp to the data-set range so the filter never returns an empty result set
        LocalDate clampedStart = selectedStartDate.isBefore(DB_MIN_DATE) ? DB_MIN_DATE : selectedStartDate;
        LocalDate clampedEnd   = selectedEndDate.isAfter(DB_MAX_DATE)    ? DB_MAX_DATE : selectedEndDate;
        dashboardViewModel.filterByDateRange(clampedStart, clampedEnd);
    }

    /** Called when a checkbox changes OR when settings prefs change visibility. */
    private void updateChartVisibility() {
        LineData lineData = binding.lineChart.getLineData();
        if (lineData != null) {
            boolean isActualChecked   = binding.cbActual.isChecked();
            boolean isForecastChecked = binding.cbForecast.isChecked();
            if (lineData.getDataSetCount() > 0)
                lineData.getDataSetByIndex(0).setVisible(isActualChecked);
            if (lineData.getDataSetCount() > 1)
                lineData.getDataSetByIndex(1).setVisible(isForecastChecked);
            binding.lineChart.invalidate();
        }
    }

    /** Read SharedPreferences and apply visibility to KPI cards and alert section. */
    private void applyDisplayPreferences() {
        if (!isAdded() || getContext() == null || binding == null) return;
        SharedPreferences p = requireContext()
            .getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE);

        // Sync chart checkboxes with saved prefs (only on first load — don't override user in-session)
        boolean savedActual   = p.getBoolean(SettingsFragment.KEY_SHOW_ACTUAL,   true);
        boolean savedForecast = p.getBoolean(SettingsFragment.KEY_SHOW_FORECAST, true);
        // Temporarily remove listeners to avoid firing applyFilter during init
        binding.cbActual.setOnCheckedChangeListener(null);
        binding.cbForecast.setOnCheckedChangeListener(null);
        binding.cbActual.setChecked(savedActual);
        binding.cbForecast.setChecked(savedForecast);
        binding.cbActual.setOnCheckedChangeListener((btn, c) -> updateChartVisibility());
        binding.cbForecast.setOnCheckedChangeListener((btn, c) -> updateChartVisibility());

        // KPI summary card group visibility
        boolean showKpi = p.getBoolean(SettingsFragment.KEY_SHOW_KPI, true);
        if (binding.getRoot().findViewWithTag("kpi_group") != null) {
            binding.getRoot().findViewWithTag("kpi_group")
                .setVisibility(showKpi ? View.VISIBLE : View.GONE);
        }
        // Try direct card IDs if they exist
        try {
            View kpiCard = binding.getRoot().findViewById(
                com.example.vapecrib.R.id.cardKpiSummary);
            if (kpiCard != null) kpiCard.setVisibility(showKpi ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {}

        // Alert badge section visibility
        boolean showAlerts = p.getBoolean(SettingsFragment.KEY_SHOW_ALERTS, true);
        try {
            View alertCard = binding.getRoot().findViewById(
                com.example.vapecrib.R.id.cardAlerts);
            if (alertCard != null) alertCard.setVisibility(showAlerts ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {}
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
