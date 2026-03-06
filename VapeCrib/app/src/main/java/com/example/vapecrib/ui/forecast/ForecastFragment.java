package com.example.vapecrib.ui.forecast;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
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

    /** Week options recalculated whenever year or month changes, matching the web's populateWeeks(). */
    private List<String> currentWeekOptions = new ArrayList<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        forecastViewModel = new ViewModelProvider(requireActivity()).get(ForecastViewModel.class);
        binding = FragmentForecastBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        configureForecastChart();
        populateDropdowns();
        applyDefaultSelections();
        observeViewModel();
        setupLoadButton();

        // Auto-load only on first creation — if the ViewModel already has chart data
        // (e.g., user navigated away and came back) keep the existing state so filters
        // and product selections survive navigation.
        LocalDate today = LocalDate.now();
        LocalDate defaultStart = LocalDate.of(today.getYear(), today.getMonthValue(), 1);
        // Use end of month (not today) so the forecast query also covers future dates.
        // This ensures the upcoming-forecast line appears even if historical per-day
        // forecasts are sparse — mirrors the web's Daily Forecast which shows the
        // full selected week (past actuals + future forecast) on the same chart.
        LocalDate defaultEnd = LocalDate.of(today.getYear(), today.getMonthValue(), today.lengthOfMonth());
        if (forecastViewModel.getForecastData().getValue() == null) {
            forecastViewModel.setSelectedProduct("All Products");
            forecastViewModel.filterByDateRange(defaultStart, defaultEnd);
        }

        // Pull-to-refresh
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            applyDefaultSelections();
            LocalDate refreshNow = LocalDate.now();
            forecastViewModel.setSelectedProduct("All Products");
            forecastViewModel.filterByDateRange(
                LocalDate.of(refreshNow.getYear(), refreshNow.getMonthValue(), 1),
                LocalDate.of(refreshNow.getYear(), refreshNow.getMonthValue(), refreshNow.lengthOfMonth()));
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
        binding.forecastLineChart.getLegend().setTextColor(Color.WHITE);
        binding.forecastLineChart.getAxisRight().setEnabled(false);
        binding.forecastLineChart.getAxisLeft().setTextColor(Color.WHITE);
        binding.forecastLineChart.getAxisLeft().setAxisMinimum(0f);
        binding.forecastLineChart.getXAxis().setTextColor(Color.WHITE);
        binding.forecastLineChart.getXAxis().setDrawGridLines(false);
        binding.forecastLineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    //  Dropdowns 

    /** Creates a no-op Filter so AutoCompleteTextView always shows all options
     *  regardless of the current text value — prevents the "only current year
     *  shows up" bug when navigating back to the fragment. */
    private <T> ArrayAdapter<T> noFilterAdapter(List<T> items) {
        return new ArrayAdapter<T>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, items) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults r = new FilterResults();
                        r.values = items;
                        r.count  = items.size();
                        return r;
                    }
                    @Override
                    protected void publishResults(CharSequence c, FilterResults r) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
    }

    private void populateDropdowns() {
        // Products — seeded initially with "All Products"; updated when ViewModel loads from API
        List<String> initial = new ArrayList<>();
        initial.add("All Products");
        binding.actvProduct.setAdapter(noFilterAdapter(initial));
        binding.actvProduct.setSaveEnabled(false);

        // Years: 2022 to currentYear+1 — no-op filter so all years always appear
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        ArrayList<String> years = new ArrayList<>();
        for (int y = 2022; y <= currentYear + 1; y++) years.add(String.valueOf(y));
        binding.actvYear.setAdapter(noFilterAdapter(years));
        binding.actvYear.setSaveEnabled(false);

        // Months — no-op filter
        List<String> monthList = new ArrayList<>();
        for (String m : MONTHS) monthList.add(m);
        binding.actvMonth.setAdapter(noFilterAdapter(monthList));
        binding.actvMonth.setSaveEnabled(false);

        // Weeks — dynamic: populated by populateWeeks() based on selected year/month.
        // Initial population uses the current year/month.
        int initYear  = Calendar.getInstance().get(Calendar.YEAR);
        int initMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
        populateWeeks(initYear, initMonth);

        // When year changes, recalculate week options for the new year/month combo.
        binding.actvYear.setOnItemClickListener((parent, view, pos, id) -> {
            String selYear  = binding.actvYear.getText().toString().trim();
            String selMonth = binding.actvMonth.getText().toString().trim();
            if (!selYear.isEmpty() && !selMonth.isEmpty()) {
                try {
                    int mo = getMonthNumber(selMonth);
                    if (mo != -1) populateWeeks(Integer.parseInt(selYear), mo);
                } catch (NumberFormatException ignored) {}
            }
        });

        // When month changes, recalculate week options for the new month.
        binding.actvMonth.setOnItemClickListener((parent, view, pos, id) -> {
            String selYear  = binding.actvYear.getText().toString().trim();
            String selMonth = binding.actvMonth.getText().toString().trim();
            if (!selYear.isEmpty() && !selMonth.isEmpty()) {
                try {
                    int mo = getMonthNumber(selMonth);
                    if (mo != -1) populateWeeks(Integer.parseInt(selYear), mo);
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    /**
     * Recalculates week options based on the actual number of days in the given month,
     * matching the web app's populateWeeks() function. Each option shows its exact
     * day range, e.g. "Week 3 (15-21)". Auto-resets the week dropdown to empty.
     */
    private void populateWeeks(int year, int month) {
        int numDays  = LocalDate.of(year, month, 1).lengthOfMonth();
        int numWeeks = (int) Math.ceil(numDays / 7.0);

        currentWeekOptions = new ArrayList<>();
        currentWeekOptions.add(""); // "All Weeks" placeholder — empty means no week filter

        for (int w = 1; w <= numWeeks; w++) {
            int dayStart = (w - 1) * 7 + 1;
            int dayEnd   = Math.min(w * 7, numDays);
            currentWeekOptions.add("Week " + w + " (" + dayStart + "-" + dayEnd + ")");
        }

        binding.actvWeek.setAdapter(noFilterAdapter(new ArrayList<>(currentWeekOptions)));
        binding.actvWeek.setText("", false);
        binding.actvWeek.setSaveEnabled(false);
    }

    /** Pre-fills the dropdowns with "All Products", the current year, and the current month.
     *  Also populates week options for the current month and auto-selects the current week.
     *  Matches the web's DOMContentLoaded initialization which pre-selects
     *  currentYear, currentMonth, and the current week number. */
    private void applyDefaultSelections() {
        LocalDate today = LocalDate.now();
        int  currentYear  = today.getYear();
        int  currentMonth = today.getMonthValue();
        String defaultYear  = String.valueOf(currentYear);
        String defaultMonth = MONTHS[currentMonth - 1];

        binding.actvProduct.setText("", false);
        binding.actvYear.setText(defaultYear, false);
        binding.actvMonth.setText(defaultMonth, false);

        // Populate weeks for current month; leave week selection blank so clicking
        // Load Forecast queries the full month — consistent with the initial auto-load
        // which always uses the first-to-last day of the selected month.
        populateWeeks(currentYear, currentMonth);
    }

    //  Observers 

    private void observeViewModel() {
        // Show/hide the SwipeRefreshLayout spinner while a background fetch is running.
        // This gives the user clear feedback that Load Forecast is working.
        forecastViewModel.getIsLoading().observe(getViewLifecycleOwner(),
                loading -> binding.swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(loading)));

        forecastViewModel.getForecastData().observe(getViewLifecycleOwner(), lineData -> {
            if (lineData != null) {
                // Clear old chart data first — mirrors web's chart.destroy() + new Chart()
                // pattern, preventing stale dataset state when navigating back to this page.
                binding.forecastLineChart.clear();
                binding.forecastLineChart.setData(lineData);
                lineData.notifyDataChanged();
                binding.forecastLineChart.notifyDataSetChanged();
                binding.forecastLineChart.getXAxis().setAxisMinimum(0);
                // Use the data's actual X range so the axis covers both the historical
                // actual line AND the extended future forecast line. The two datasets
                // can have different entry counts (actual stops at last real sale;
                // forecast continues into future slots), so getXMax() is correct here
                // rather than using entryCount-1 from either individual dataset.
                binding.forecastLineChart.getXAxis().setAxisMaximum(lineData.getXMax());
                binding.forecastLineChart.animateX(500);
            } else {
                binding.forecastLineChart.clear();
                binding.forecastLineChart.invalidate();
            }
        });

        // When the ViewModel finishes loading real product names, refresh the dropdown
        // using the same no-op filter so all products are always visible in the list
        forecastViewModel.getProductNames().observe(getViewLifecycleOwner(), names -> {
            if (names == null || names.isEmpty()) return;
            List<String> namesCopy = new ArrayList<>(names);
            binding.actvProduct.setAdapter(noFilterAdapter(namesCopy));
            binding.actvProduct.setSaveEnabled(false);
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
        LocalDate end = null;

        // Narrow to week if a specific week is chosen.
        // Parse the dynamic format "Week N (D1-D2)" produced by populateWeeks().
        if (!weekText.isEmpty()) {
            int parenStart = weekText.indexOf('(');
            int parenEnd   = weekText.indexOf(')');
            if (parenStart >= 0 && parenEnd > parenStart) {
                try {
                    String[] parts = weekText.substring(parenStart + 1, parenEnd).split("-");
                    int dayStart = Integer.parseInt(parts[0].trim());
                    int dayEnd   = Integer.parseInt(parts[1].trim());
                    start = LocalDate.of(year, month, dayStart);
                    end   = LocalDate.of(year, month, dayEnd);
                } catch (Exception ignored) {} // fall through to full-month range
            }
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
