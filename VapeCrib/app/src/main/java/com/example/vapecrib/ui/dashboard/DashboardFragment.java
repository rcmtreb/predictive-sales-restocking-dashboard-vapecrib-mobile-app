package com.example.vapecrib.ui.dashboard;

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
import com.github.mikephil.charting.data.LineData;

import java.util.Calendar;

public class DashboardFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private FragmentDashboardBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

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
            binding.lineChart.setData(lineData);
            updateChartVisibility();
            binding.lineChart.invalidate(); // refresh
        });
        dashboardViewModel.getMonthlyPerformanceData().observe(getViewLifecycleOwner(), barData -> {
            binding.barChart.setData(barData);
            binding.barChart.invalidate(); // refresh
        });

        // Time Period Dropdown
        String[] timePeriods = getResources().getStringArray(R.array.time_period_options);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, timePeriods);
        binding.actvTimePeriod.setAdapter(adapter);

        // Date Click Listeners
        binding.etStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        binding.etEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // Checkbox Listeners
        binding.cbActual.setOnCheckedChangeListener((buttonView, isChecked) -> updateChartVisibility());
        binding.cbForecast.setOnCheckedChangeListener((buttonView, isChecked) -> updateChartVisibility());

        return root;
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
            binding.lineChart.invalidate(); // refresh
        }
    }

    private void showDatePickerDialog(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(getContext(), (view, year1, month1, dayOfMonth) -> {
            String selectedDate = (month1 + 1) + "/" + dayOfMonth + "/" + year1;
            if (isStartDate) {
                binding.etStartDate.setText(selectedDate);
            } else {
                binding.etEndDate.setText(selectedDate);
            }
        }, year, month, day);
        datePickerDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
