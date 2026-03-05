package com.example.vapecrib.ui.forecast;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.vapecrib.databinding.FragmentForecastBinding;
import com.github.mikephil.charting.components.XAxis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ForecastFragment extends Fragment {

    private static final LocalDate CSV_MIN_DATE = LocalDate.of(2022, 1, 1);
    private static final LocalDate CSV_MAX_DATE = LocalDate.of(2026, 3, 31);

    private ForecastViewModel forecastViewModel;
    private FragmentForecastBinding binding;

    private static final String[] MONTHS = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };
    private static final String[] WEEKS = {
        "All Weeks","Week 1 (Days 1-7)","Week 2 (Days 8-14)",
        "Week 3 (Days 15-21)","Week 4 (Days 22-28)","Week 5 (Days 29-31)"
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        forecastViewModel = new ViewModelProvider(this).get(ForecastViewModel.class);
        binding = FragmentForecastBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        configureForecastChart();
        populateDropdowns();
        observeViewModel();
        setupLoadButton();

        // Pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            binding.actvProduct.setText("", false);
            binding.actvYear.setText("", false);
            binding.actvMonth.setText("", false);
            binding.actvWeek.setText("", false);

            forecastViewModel.setSelectedProduct("All Products");
            forecastViewModel.filterByDateRange(CSV_MIN_DATE, CSV_MAX_DATE);
            binding.swipeRefreshLayout.setRefreshing(false);
        });

        return root;
    }

    //  Chart style 

    private void configureForecastChart() {
        binding.forecastLineChart.getDescription().setEnabled(false);
        binding.forecastLineChart.setTouchEnabled(true);
        binding.forecastLineChart.setDragEnabled(true);
        binding.forecastLineChart.setScaleEnabled(true);
        binding.forecastLineChart.setPinchZoom(true);
        binding.forecastLineChart.getLegend().setEnabled(true);
        binding.forecastLineChart.getLegend().setTextColor(android.graphics.Color.WHITE);
        binding.forecastLineChart.getAxisRight().setEnabled(false);
        binding.forecastLineChart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        binding.forecastLineChart.getAxisLeft().setAxisMinimum(0f);
        binding.forecastLineChart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        binding.forecastLineChart.getXAxis().setDrawGridLines(false);
        binding.forecastLineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    //  Dropdowns 

    private void populateDropdowns() {
        // Products — seeded initially with "All Products"; updated when ViewModel loads from API
        List<String> initial = new ArrayList<>();
        initial.add("All Products");
        ArrayAdapter<String> productAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_dropdown_item_1line, initial);
        binding.actvProduct.setAdapter(productAdapter);

        // Years: 2022 to currentYear+1
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        ArrayList<String> years = new ArrayList<>();
        for (int y = 2022; y <= currentYear + 1; y++) years.add(String.valueOf(y));
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_dropdown_item_1line, years);
        binding.actvYear.setAdapter(yearAdapter);

        // Months
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_dropdown_item_1line, MONTHS);
        binding.actvMonth.setAdapter(monthAdapter);

        // Weeks
        ArrayAdapter<String> weekAdapter = new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_dropdown_item_1line, WEEKS);
        binding.actvWeek.setAdapter(weekAdapter);
    }

    //  Observers 

    private void observeViewModel() {
        forecastViewModel.getForecastData().observe(getViewLifecycleOwner(), lineData -> {
            if (lineData != null) {
                binding.forecastLineChart.setData(lineData);
                binding.forecastLineChart.getXAxis().setAxisMinimum(0);
                int entryCount = lineData.getDataSetByIndex(0).getEntryCount();
                binding.forecastLineChart.getXAxis().setAxisMaximum(entryCount - 1);
                binding.forecastLineChart.animateX(500);
            } else {
                binding.forecastLineChart.clear();
                binding.forecastLineChart.invalidate();
            }
        });

        // When the ViewModel finishes loading real product names, refresh the dropdown
        forecastViewModel.getProductNames().observe(getViewLifecycleOwner(), names -> {
            if (names == null || names.isEmpty()) return;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, names);
            binding.actvProduct.setAdapter(adapter);
        });
    }

    //  Load button 

    private void setupLoadButton() {
        binding.btnLoadForecast.setOnClickListener(v -> applyForecastFilter());
    }

    private void applyForecastFilter() {
        String productText = binding.actvProduct.getText().toString().trim();
        String yearText    = binding.actvYear.getText().toString().trim();
        String monthText   = binding.actvMonth.getText().toString().trim();
        String weekText    = binding.actvWeek.getText().toString().trim();

        if (yearText.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a year", Toast.LENGTH_SHORT).show();
            return;
        }
        if (monthText.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a month", Toast.LENGTH_SHORT).show();
            return;
        }

        int year;
        try { year = Integer.parseInt(yearText); }
        catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid year", Toast.LENGTH_SHORT).show();
            return;
        }

        int month = getMonthNumber(monthText);
        if (month == -1) {
            Toast.makeText(requireContext(), "Invalid month", Toast.LENGTH_SHORT).show();
            return;
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end;

        // Narrow to week if a specific week is chosen
        if (!weekText.isEmpty() && !weekText.equals("All Weeks")) {
            int weekNum = getWeekNumber(weekText);
            int dayStart = (weekNum - 1) * 7 + 1;
            int dayEnd   = Math.min(dayStart + 6, start.lengthOfMonth());
            start = LocalDate.of(year, month, dayStart);
            end   = LocalDate.of(year, month, dayEnd);
        } else {
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        // Clamp to sample data range Jan-2025..Feb-2026
        LocalDate dataMin = CSV_MIN_DATE;
        LocalDate dataMax = CSV_MAX_DATE;
        if (end.isBefore(dataMin) || start.isAfter(dataMax)) {
            Toast.makeText(requireContext(),
                "No data for that period. Data covers Jan 2022 to Mar 2026.",
                Toast.LENGTH_LONG).show();
            return;
        }
        start = start.isBefore(dataMin) ? dataMin : start;
        end   = end.isAfter(dataMax)    ? dataMax : end;

        // Update product selection
        forecastViewModel.setSelectedProduct(productText.isEmpty() ? "All Products" : productText);

        forecastViewModel.filterByDateRange(start, end);
        Toast.makeText(requireContext(),
            "Loaded: " + productText + "  " + monthText + " " + year,
            Toast.LENGTH_SHORT).show();
    }

    private int getMonthNumber(String name) {
        for (int i = 0; i < MONTHS.length; i++)
            if (MONTHS[i].equalsIgnoreCase(name)) return i + 1;
        return -1;
    }

    private int getWeekNumber(String weekText) {
        if (weekText.startsWith("Week 1")) return 1;
        if (weekText.startsWith("Week 2")) return 2;
        if (weekText.startsWith("Week 3")) return 3;
        if (weekText.startsWith("Week 4")) return 4;
        if (weekText.startsWith("Week 5")) return 5;
        return 1;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
