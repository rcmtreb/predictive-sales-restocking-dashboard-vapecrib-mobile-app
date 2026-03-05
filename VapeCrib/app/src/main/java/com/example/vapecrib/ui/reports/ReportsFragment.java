package com.example.vapecrib.ui.reports;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.vapecrib.databinding.FragmentReportsBinding;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

@SuppressLint("NewApi")
public class ReportsFragment extends Fragment {

    private static final LocalDate CSV_MIN_DATE = LocalDate.of(2022, 1, 1);
    private static final LocalDate CSV_MAX_DATE = LocalDate.of(2026, 3, 31);

    private ReportsViewModel reportsViewModel;
    private FragmentReportsBinding binding;
    private LocalDate selectedStartDate = null;
    private LocalDate selectedEndDate = null;

    @Override
    @SuppressLint("NewApi")
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        binding = FragmentReportsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Dates start null — no pre-fill, all data shown by default

        // KPI Observers
        reportsViewModel.getCriticalCount().observe(getViewLifecycleOwner(),  binding.tvCriticalCount::setText);
        reportsViewModel.getHighCount().observe(getViewLifecycleOwner(),      binding.tvHighCount::setText);
        reportsViewModel.getMediumCount().observe(getViewLifecycleOwner(),    binding.tvMediumCount::setText);
        reportsViewModel.getLowCount().observe(getViewLifecycleOwner(),       binding.tvLowCount::setText);
        reportsViewModel.getExpiringItems().observe(getViewLifecycleOwner(),  binding.tvExpiringItems::setText);
        reportsViewModel.getTotalRevenue().observe(getViewLifecycleOwner(),
                v -> binding.tvTotalRevenue.setText("Revenue: " + v));
        reportsViewModel.getTotalProducts().observe(getViewLifecycleOwner(),
                v -> binding.tvTotalProducts.setText("Products Sold: " + v));
        reportsViewModel.getApiError().observe(getViewLifecycleOwner(),
                msg -> { if (msg != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show(); });

        // Time Period Dropdown — preset options only, Start/End serve as custom range
        String[] timePeriods = {"All Time", "Last 30 Days", "Last 90 Days",
                "Last 6 Months", "Last Year"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, timePeriods);
        binding.actvTimePeriod.setAdapter(adapter);
        binding.actvTimePeriod.setOnItemClickListener(
                (parent, view, position, id) -> handleTimePeriodSelection(position));

        binding.etStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        binding.etEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // Pull-to-refresh — reload all data without any date filter
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            selectedStartDate = null;
            selectedEndDate = null;
            binding.etStartDate.setText("");
            binding.etEndDate.setText("");
            binding.actvTimePeriod.setText("", false);
            reportsViewModel.filterByDateRange(CSV_MIN_DATE, CSV_MAX_DATE);
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    private void handleTimePeriodSelection(int position) {
        LocalDate today = LocalDate.now();
        LocalDate periodStart;
        LocalDate periodEnd;
        switch (position) {
            case 0: periodStart = CSV_MIN_DATE;       periodEnd = CSV_MAX_DATE; break; // All Time — full CSV data range
            case 1: periodStart = today.minusDays(30);  periodEnd = today; break;
            case 2: periodStart = today.minusDays(90);  periodEnd = today; break;
            case 3: periodStart = today.minusMonths(6); periodEnd = today; break;
            case 4: periodStart = today.minusYears(1);  periodEnd = today; break;
            default: return;
        }

        selectedStartDate = null;
        selectedEndDate = null;
        binding.etStartDate.setText("");
        binding.etEndDate.setText("");

        reportsViewModel.filterByDateRange(periodStart, periodEnd);
    }

    private void updateDateDisplay() {
        if (binding == null) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        binding.etStartDate.setText(selectedStartDate != null ? selectedStartDate.format(fmt) : "");
        binding.etEndDate.setText(selectedEndDate   != null ? selectedEndDate.format(fmt)   : "");
    }

    private void showDatePickerDialog(boolean isStartDate) {
        if (!isAdded() || getContext() == null) return;
        // Seed the picker on the already-chosen date if available, otherwise today
        LocalDate seed = isStartDate
                ? (selectedStartDate != null ? selectedStartDate : LocalDate.now())
                : (selectedEndDate   != null ? selectedEndDate   : LocalDate.now());
        Calendar cal = Calendar.getInstance();
        cal.set(seed.getYear(), seed.getMonthValue() - 1, seed.getDayOfMonth());
        new DatePickerDialog(getContext(),
                (view, year, month, day) -> {
                    if (binding == null) return;   // fragment already destroyed
                    LocalDate picked = LocalDate.of(year, month + 1, day);
                    // Clamp to data range so the filter always yields results
                    picked = picked.isBefore(CSV_MIN_DATE) ? CSV_MIN_DATE
                           : picked.isAfter(CSV_MAX_DATE)  ? CSV_MAX_DATE : picked;
                    if (isStartDate) selectedStartDate = picked;
                    else             selectedEndDate   = picked;
                    binding.actvTimePeriod.setText("", false);
                    updateDateDisplay();
                    applyFilter();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void applyFilter() {
        if (selectedStartDate == null || selectedEndDate == null) return;
        if (selectedEndDate.isBefore(selectedStartDate)) {
            LocalDate tmp     = selectedStartDate;
            selectedStartDate = selectedEndDate;
            selectedEndDate   = tmp;
            updateDateDisplay();
        }
        // Ensure the range is always within the data-set boundaries
        LocalDate clampedStart = selectedStartDate.isBefore(CSV_MIN_DATE) ? CSV_MIN_DATE : selectedStartDate;
        LocalDate clampedEnd   = selectedEndDate.isAfter(CSV_MAX_DATE)    ? CSV_MAX_DATE : selectedEndDate;
        reportsViewModel.filterByDateRange(clampedStart, clampedEnd);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

